package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.domain.SensitiveQueryLog;
import ru.sapa.gadalka_backend.domain.type.DetectionSource;
import ru.sapa.gadalka_backend.domain.type.SensitiveContentCategory;
import ru.sapa.gadalka_backend.repository.SensitiveQueryLogRepository;

import java.time.OffsetDateTime;

/**
 * Выделен в отдельный бин намеренно — только ради того, чтобы {@code @Transactional(REQUIRES_NEW)}
 * реально работал.
 *
 * <p>Spring реализует декларативные транзакции через прокси вокруг бина. Если метод с
 * REQUIRES_NEW вызывается как обычный метод ТОГО ЖЕ класса (self-invocation, например
 * {@code logSensitiveQuery(...)} без {@code this.} внутри {@link SensitiveContentFilterService}),
 * вызов идёт в обход прокси напрямую на объект — аннотация тихо игнорируется, и метод
 * выполняется в транзакции вызывающего кода, а не в новой.
 *
 * <p>Из-за этого лог блокировки, записанный внутри {@code @Transactional}-методов
 * {@code FortuneService.getFortune} / {@code DreamService.analyzeDream}, откатывался
 * вместе со всей их транзакцией в момент, когда сразу следом бросался
 * {@code SensitiveContentBlockedException} — запись исчезала из БД, хотя пользователю
 * вопрос корректно блокировался. Вызов отдельного бина (этого класса) всегда идёт
 * через прокси Spring — self-invocation здесь невозможен по построению.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveQueryLogWriter {

    private final SensitiveQueryLogRepository sensitiveQueryLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SensitiveQueryLog save(Long userId, String question, SensitiveContentCategory category,
                                   DetectionSource source, String rawClassificationOutput, String explanation) {
        SensitiveQueryLog saved = sensitiveQueryLogRepository.save(SensitiveQueryLog.builder()
                .userId(userId)
                .question(question)
                .category(category)
                .source(source)
                .rawClassificationOutput(rawClassificationOutput)
                .explanation(explanation)
                .build());
        log.info("Залогирован чувствительный запрос: userId={}, category={}, source={}", userId, category, source);
        return saved;
    }

    /**
     * Запись при бэкафилле — то же самое, но с явным {@code detectedAt}, выставленным
     * в дату исходного вопроса (не "сейчас"): иначе вся история в админке выглядела бы
     * так, будто все старые вопросы обнаружены сегодня.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SensitiveQueryLog saveBackfillEntry(Long userId, String question, SensitiveContentCategory category,
                                                DetectionSource source, String rawClassificationOutput,
                                                String explanation, OffsetDateTime detectedAt) {
        SensitiveQueryLog saved = sensitiveQueryLogRepository.save(SensitiveQueryLog.builder()
                .userId(userId)
                .question(question)
                .category(category)
                .source(source)
                .rawClassificationOutput(rawClassificationOutput)
                .explanation(explanation)
                .detectedAt(detectedAt)
                .build());
        log.info("Бэкафилл: залогирован исторический чувствительный запрос: userId={}, category={}, source={}",
                userId, category, source);
        return saved;
    }
}
