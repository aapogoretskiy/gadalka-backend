package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.domain.SensitiveQueryLog;
import ru.sapa.gadalka_backend.domain.type.SensitiveContentCategory;
import ru.sapa.gadalka_backend.repository.SensitiveQueryLogRepository;
import ru.sapa.gadalka_backend.service.interpretation.AiInterpretationManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static ru.sapa.gadalka_backend.constant.SystemConfigConstants.AI_PROVIDER;

/**
 * Многоуровневый фильтр чувствительного контента.
 *
 * <p>Уровень 1 — {@link #detectByKeywords}: мгновенно, бесплатно, без LLM.
 * Ловит очевидные случаи по ключевым корням.
 *
 * <p>Уровень 2 — {@link #isLlmRefusal}: анализирует ответ LLM на паттерны отказа.
 * Вызывается уже после LLM-запроса — без доп. затрат.
 *
 * <p>Уровень 3 — {@link #classifyByLlm}: дешёвый отдельный LLM-вызов (~200 токенов).
 * Срабатывает только в редком случае: keyword-фильтр промахнулся, но LLM всё равно отказал.
 * Нужен исключительно для корректного логирования категории.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveContentFilterService {

    private final SensitiveQueryLogRepository sensitiveQueryLogRepository;
    private final AiInterpretationManager interpretationManager;
    private final SystemConfigService systemConfigService;

    // ── Ключевые слова по категориям ───────────────────────────────────────
    // Нормализованные корни (будут нормализованы вместе с текстом пользователя).
    // LEGAL_FINANCIAL_ADVICE и LLM_REFUSED — контекстуальные, без keyword-фильтра.

    // Аббревиатуры и короткие слова, которые ищем ТОЛЬКО как отдельное слово.
    // Важно: "сво" как подстрока ложно матчит "своих", "своего", "свое" и т.д.,
    // поэтому вынесено сюда с проверкой по точному совпадению токена.
    private static final Map<SensitiveContentCategory, Set<String>> KEYWORD_EXACT_WORDS =
            Map.of(
                    SensitiveContentCategory.MILITARY_CONFLICT, Set.of("сво", "нато")
            );

    private static final Map<SensitiveContentCategory, Set<String>> KEYWORD_ROOTS =
            Map.ofEntries(
                    Map.entry(SensitiveContentCategory.MILITARY_CONFLICT, Set.of(
                            "спецоперац", "донбас", "луганск", "мобилиз",
                            "военнопленн", "укрфронт"
                    )),
                    Map.entry(SensitiveContentCategory.DEATH_MORTALITY, Set.of(
                            "умру", "умрет", "умереть",
                            "сколько проживу", "когда умру", "доживу ли",
                            "скоро умр", "дата смерти"
                    )),
                    Map.entry(SensitiveContentCategory.SELF_HARM_SUICIDE, Set.of(
                            "суицид", "самоубийств", "покончить с собой", "убить себя",
                            "повеситься", "прыгнуть с крыши", "вскрыть вен",
                            "причинить себе вред", "покончить жизнь"
                    )),
                    Map.entry(SensitiveContentCategory.HEALTH_MEDICAL, Set.of(
                            "диагноз поставят", "поставят диагноз", "вылечусь ли",
                            "вылечит ли", "химиотерап", "онкол", "опухол", "метастаз",
                            "вылечиться от рак", "выживу ли после операц"
                    )),
                    Map.entry(SensitiveContentCategory.CRIME_VIOLENCE, Set.of(
                            "убийств", "ограбл", "изнасилов", "маньяк", "педофил",
                            "терроризм", "взрыв устроить"
                    )),
                    Map.entry(SensitiveContentCategory.GAMBLING_INVESTMENT, Set.of(
                            "ставк на спорт", "лотерея выиграю"
                    )),
                    Map.entry(SensitiveContentCategory.POLITICAL_RELIGIOUS, Set.of(
                            "путин", "навальн", "кремл", "оппозиц"
                    )),
                    Map.entry(SensitiveContentCategory.MISSING_PERSONS_GUILT, Set.of(
                            "найти пропавш", "пропал человек", "кто убил", "кто виноват в убийстве",
                            "виновен ли", "где находится пропавш"
                    ))
            );

    // ── Паттерны отказа в ответах LLM ─────────────────────────────────────

    private static final List<Pattern> REFUSAL_PATTERNS = List.of(
            // "я" обязателен — иначе "он не может ответить себе" ловится как отказ LLM
            Pattern.compile("(?i)(я не могу ответить|не в моих силах|я не буду отвечать)"),
            Pattern.compile("(?i)(это выходит за (пределы|рамки)|этот вопрос (вне|за пределами))"),
            Pattern.compile("(?i)(обратитесь к (специалисту|врачу|юристу|психологу|профессионалу))"),
            Pattern.compile("(?i)(карты не могут (ответить|помочь) (на этот|с этим))"),
            Pattern.compile("(?i)(я не могу (дать|предоставить) (профессиональн|медицинск|юридическ))")
    );

    private static final Pattern NORMALIZE_PATTERN = Pattern.compile("[^а-яa-z0-9 ]");

    /**
     * Проверяет вопрос по ключевым корням — до вызова LLM, бесплатно.
     *
     * <p>Сначала проверяет аббревиатуры/короткие слова из {@code KEYWORD_EXACT_WORDS}
     * по точному совпадению токена (чтобы "сво" не матчило "своих").
     * Затем проверяет корни из {@code KEYWORD_ROOTS} по вхождению подстроки.
     *
     * @return категория чувствительного контента, либо empty если вопрос безопасен
     */
    public Optional<SensitiveContentCategory> detectByKeywords(String question) {
        if (question == null || question.isBlank()) return Optional.empty();

        String normalized = normalize(question);

        // Уровень 1а: точное совпадение токена (аббревиатуры)
        for (Map.Entry<SensitiveContentCategory, Set<String>> entry : KEYWORD_EXACT_WORDS.entrySet()) {
            for (String word : entry.getValue()) {
                if (containsExactWord(normalized, normalize(word))) {
                    log.info("Keyword-фильтр (точное слово): '{}', категория={}", word, entry.getKey());
                    return Optional.of(entry.getKey());
                }
            }
        }

        // Уровень 1б: вхождение корня/подстроки
        for (Map.Entry<SensitiveContentCategory, Set<String>> entry : KEYWORD_ROOTS.entrySet()) {
            for (String root : entry.getValue()) {
                if (normalized.contains(normalize(root))) {
                    log.info("Keyword-фильтр (корень): '{}', категория={}", root, entry.getKey());
                    return Optional.of(entry.getKey());
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Проверяет ответ LLM на паттерны отказа — бесплатно, без доп. токенов.
     *
     * @param response текст ответа LLM
     * @return true, если LLM отказал
     */
    public boolean isLlmRefusal(String response) {
        if (response == null || response.isBlank()) return false;

        for (Pattern pattern : REFUSAL_PATTERNS) {
            if (pattern.matcher(response).find()) {
                log.info("Обнаружен отказ LLM по паттерну: {}", pattern.pattern());
                return true;
            }
        }
        return false;
    }

    /**
     * Классифицирует вопрос через дешёвый LLM-вызов.
     * Вызывается только когда keyword-фильтр промахнулся, а LLM отказал.
     * Стоимость: ~200 входных + ~10 выходных токенов.
     */
    public SensitiveContentCategory classifyByLlm(String question) {
        try {
            String provider = systemConfigService.getValue(AI_PROVIDER);
            String raw = interpretationManager.classifySensitiveContent(provider, question);
            return parseCategory(raw);
        } catch (Exception e) {
            log.warn("Не удалось классифицировать вопрос через LLM: {}", e.getMessage());
            return SensitiveContentCategory.LLM_REFUSED;
        }
    }

    /**
     * Логирует чувствительный запрос в БД.
     * Выполняется в <b>отдельной транзакции</b> — запись фиксируется независимо
     * от того, откатится ли внешняя транзакция (например, при броске исключения).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSensitiveQuery(Long userId, String question, SensitiveContentCategory category) {
        sensitiveQueryLogRepository.save(SensitiveQueryLog.builder()
                .userId(userId)
                .question(question)
                .category(category)
                .build());
        log.info("Залогирован чувствительный запрос: userId={}, category={}", userId, category);
    }

    /**
     * Проверяет, содержит ли нормализованный текст слово {@code word} как отдельный токен.
     * Токены разделяются пробелами (текст уже нормализован, знаки препинания удалены).
     */
    private boolean containsExactWord(String normalizedText, String word) {
        for (String token : normalizedText.split(" +")) {
            if (token.equals(word)) return true;
        }
        return false;
    }

    private String normalize(String text) {
        return NORMALIZE_PATTERN
                .matcher(text.toLowerCase().replace('ё', 'е'))
                .replaceAll("");
    }

    private SensitiveContentCategory parseCategory(String raw) {
        if (raw == null) return SensitiveContentCategory.LLM_REFUSED;
        try {
            return SensitiveContentCategory.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("LLM вернул неизвестную категорию: '{}', используем LLM_REFUSED", raw);
            return SensitiveContentCategory.LLM_REFUSED;
        }
    }
}
