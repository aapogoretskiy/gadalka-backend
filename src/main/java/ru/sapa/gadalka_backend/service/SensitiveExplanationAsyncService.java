package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.domain.type.SensitiveContentCategory;
import ru.sapa.gadalka_backend.repository.SensitiveQueryLogRepository;
import ru.sapa.gadalka_backend.service.interpretation.AiInterpretationManager;

import static ru.sapa.gadalka_backend.constant.SystemConfigConstants.AI_PROVIDER;

/**
 * Вынесено в отдельный бин намеренно: {@code @Async} работает через Spring AOP-прокси,
 * а self-invocation (вызов "асинхронного" метода из другого метода ТОГО ЖЕ класса, как
 * это было бы, останься этот метод внутри {@link SensitiveContentFilterService}) прокси
 * не перехватывает — метод просто выполнился бы синхронно, и вся идея "не тормозить
 * ответ пользователю ради объяснения для админки" была бы сломана незаметно для тестов.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveExplanationAsyncService {

    private final SensitiveQueryLogRepository sensitiveQueryLogRepository;
    private final AiInterpretationManager interpretationManager;
    private final SystemConfigService systemConfigService;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fetchAndAttachExplanationAsync(Long logId, String question, SensitiveContentCategory category) {
        try {
            String provider = systemConfigService.getValue(AI_PROVIDER);
            String explanation = interpretationManager.explainSensitiveClassification(provider, question, category.name());
            sensitiveQueryLogRepository.findById(logId).ifPresent(entry -> {
                entry.setExplanation(explanation);
                sensitiveQueryLogRepository.save(entry);
            });
        } catch (Exception e) {
            log.warn("Не удалось получить объяснение для sensitive_query_log id={}: {}", logId, e.getMessage());
        }
    }
}
