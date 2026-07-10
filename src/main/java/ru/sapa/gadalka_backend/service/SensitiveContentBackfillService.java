package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.domain.DreamReading;
import ru.sapa.gadalka_backend.domain.Fortune;
import ru.sapa.gadalka_backend.domain.type.DetectionSource;
import ru.sapa.gadalka_backend.domain.type.SensitiveContentCategory;
import ru.sapa.gadalka_backend.repository.DreamReadingRepository;
import ru.sapa.gadalka_backend.repository.FortuneRepository;
import ru.sapa.gadalka_backend.repository.SensitiveQueryLogRepository;

/**
 * Разовый (перезапускаемый) проход по УЖЕ существующим вопросам — восполняет то, что
 * действующий на момент вопроса keyword-фильтр пропустил (классический пример —
 * "жив ли Владимир 19.11.1970 года рождения", см. историю обсуждения фичи).
 *
 * <p>Источники свободного текста — ровно два: {@code fortunes.question} и
 * {@code dream_readings.dream_text} (у совместимости и нумерологии свободного текста нет).
 *
 * <p>На каждый вопрос — сначала keyword (бесплатно), и только если keyword «чист» —
 * LLM-классификатор (тот же {@link SensitiveContentFilterService#classifyByLlmPreCheck}/
 * {@code ...ForDream}, что и в реальном времени). Не гоняем LLM ПОВЕРХ уже сработавшего
 * keyword — там результат детерминирован и так, а цель LLM здесь именно в том, чтобы
 * поймать случаи, которые keyword пропускает, а не переподтвердить то, что он уже нашёл.
 *
 * <p>Дедуп по {@code (userId, question)} — безопасно перезапускать: то, что уже залогировано
 * (в реальном времени или предыдущим прогоном бэкафилла), не задваивается.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveContentBackfillService {

    private static final int PAGE_SIZE = 200;

    private final FortuneRepository fortuneRepository;
    private final DreamReadingRepository dreamReadingRepository;
    private final SensitiveQueryLogRepository sensitiveQueryLogRepository;
    private final SensitiveContentFilterService sensitiveContentFilterService;
    private final UserSensitivityProfileService userSensitivityProfileService;

    public record BackfillResult(int scannedFortunes, int scannedDreams,
                                  int newlyLogged, int classificationFailures) {}

    /**
     * Не помечен {@code @Transactional}: сама операция долгая (постраничное чтение +
     * потенциально много блокирующих HTTP-вызовов к AI), а каждая запись в лог уже
     * фиксируется независимо через {@code REQUIRES_NEW} — оборачивать всё в одну большую
     * транзакцию означало бы держать соединение с БД открытым на всё время прогона.
     */
    public BackfillResult runFullBackfill() {
        Counters counters = new Counters();

        int page = 0;
        Page<Fortune> fortunePage;
        do {
            fortunePage = fortuneRepository.findAll(PageRequest.of(page, PAGE_SIZE, Sort.by("id")));
            for (Fortune fortune : fortunePage.getContent()) {
                counters.scannedFortunes++;
                processFreeText(fortune.getUserId(), fortune.getQuestion(), fortune.getCreatedAt(), false, counters);
            }
            page++;
        } while (fortunePage.hasNext());

        page = 0;
        Page<DreamReading> dreamPage;
        do {
            dreamPage = dreamReadingRepository.findAll(PageRequest.of(page, PAGE_SIZE, Sort.by("id")));
            for (DreamReading dream : dreamPage.getContent()) {
                counters.scannedDreams++;
                processFreeText(dream.getUserId(), dream.getDreamText(), dream.getCreatedAt(), true, counters);
            }
            page++;
        } while (dreamPage.hasNext());

        log.info("Бэкафилл завершён: fortunes={}, dreams={}, найдено новых={}, сбоев классификации={}",
                counters.scannedFortunes, counters.scannedDreams, counters.newlyLogged, counters.classificationFailures);

        // Исторические записи изменили картину по пользователям — пересчитываем профили целиком
        userSensitivityProfileService.recomputeAllProfiles();

        return new BackfillResult(counters.scannedFortunes, counters.scannedDreams,
                counters.newlyLogged, counters.classificationFailures);
    }

    private void processFreeText(Long userId, String text, java.time.OffsetDateTime originalCreatedAt,
                                  boolean isDream, Counters counters) {
        if (text == null || text.isBlank()) return;
        if (sensitiveQueryLogRepository.existsByUserIdAndQuestion(userId, text)) return;

        var keywordMatch = isDream
                ? sensitiveContentFilterService.detectByKeywordsForDreamWithMatch(text)
                : sensitiveContentFilterService.detectByKeywordsWithMatch(text);

        if (keywordMatch.isPresent()) {
            sensitiveContentFilterService.logBackfillEntry(userId, text, keywordMatch.get().category(),
                    DetectionSource.BACKFILL_KEYWORD, null,
                    "Сработало ключевое слово: \"" + keywordMatch.get().matchedTerm() + "\"", originalCreatedAt);
            counters.newlyLogged++;
            return;
        }

        var llmResult = isDream
                ? sensitiveContentFilterService.classifyByLlmPreCheckForDream(text)
                : sensitiveContentFilterService.classifyByLlmPreCheck(text);

        if (llmResult.category() == SensitiveContentCategory.CLASSIFICATION_FAILED) {
            counters.classificationFailures++;
            sensitiveContentFilterService.logBackfillEntry(userId, text, SensitiveContentCategory.CLASSIFICATION_FAILED,
                    DetectionSource.BACKFILL_LLM, llmResult.rawOutput(), null, originalCreatedAt);
            return;
        }

        if (llmResult.isBlocked()) {
            String explanation = sensitiveContentFilterService.explainForBackfill(text, llmResult.category());
            sensitiveContentFilterService.logBackfillEntry(userId, text, llmResult.category(),
                    DetectionSource.BACKFILL_LLM, null, explanation, originalCreatedAt);
            counters.newlyLogged++;
        }
    }

    private static class Counters {
        int scannedFortunes;
        int scannedDreams;
        int newlyLogged;
        int classificationFailures;
    }
}
