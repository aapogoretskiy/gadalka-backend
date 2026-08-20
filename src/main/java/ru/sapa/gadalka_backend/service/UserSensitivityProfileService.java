package ru.sapa.gadalka_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.domain.SensitiveQueryLog;
import ru.sapa.gadalka_backend.domain.UserSensitivityProfile;
import ru.sapa.gadalka_backend.domain.type.RiskLevel;
import ru.sapa.gadalka_backend.domain.type.SensitiveContentCategory;
import ru.sapa.gadalka_backend.repository.DreamReadingRepository;
import ru.sapa.gadalka_backend.repository.FortuneRepository;
import ru.sapa.gadalka_backend.repository.SensitiveQueryLogRepository;
import ru.sapa.gadalka_backend.repository.UserSensitivityProfileRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * "Рейтинг склонности к чувствительным вопросам" — агрегат по {@link SensitiveQueryLog}
 * на пользователя, для мониторинга в админке (см. историю обсуждения фичи).
 *
 * <p>Числитель/дом. категория — только по подтверждённым категориям (9 реальных тем),
 * {@code CLASSIFICATION_FAILED} и {@code LLM_REFUSED} в подсчёт НЕ идут: это технические
 * пометки ("формат ответа LLM не сошёлся"), а не подтверждённая классификация темы —
 * включать их в рейтинг было бы некорректно (сырые записи для разбора всё равно видны
 * в /api/admin/sensitive-queries, просто не участвуют в проценте/цвете).
 *
 * <p>Знаменатель — только вопросы со свободным текстом (fortunes.question +
 * dream_readings.dream_text), а не {@code User.totalActionsCount}: нумерология/гороскоп
 * не дают пользователю возможности задать чувствительный вопрос вообще, включение их
 * в знаменатель искусственно занижало бы процент.
 *
 * <p>{@code riskLevel = RED} принудительно (override), если среди категорий пользователя
 * встречается {@link SensitiveContentCategory#SELF_HARM_SUICIDE} — независимо от процента.
 * Иначе — по порогам: 0–3% GREEN, 3–10% YELLOW, {@literal >}10% RED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSensitivityProfileService {

    private static final BigDecimal YELLOW_THRESHOLD_PERCENT = new BigDecimal("3");
    private static final BigDecimal RED_THRESHOLD_PERCENT = new BigDecimal("10");

    private final SensitiveQueryLogRepository sensitiveQueryLogRepository;
    private final UserSensitivityProfileRepository userSensitivityProfileRepository;
    private final FortuneRepository fortuneRepository;
    private final DreamReadingRepository dreamReadingRepository;
    private final ObjectMapper objectMapper;

    /**
     * Пересчитывает профиль одного пользователя — вызывается после каждой live-детекции.
     *
     * <p>{@code REQUIRES_NEW} обязателен: вызывается из {@code logKeywordMatch}/{@code logLlmDetection},
     * которые сами вызываются из середины {@code @Transactional} методов ({@code FortuneService.getFortune},
     * {@code DreamService.analyzeDream}) прямо перед тем, как метод бросит
     * {@code SensitiveContentBlockedException} и вся внешняя транзакция откатится. Без
     * {@code REQUIRES_NEW} пересчёт профиля откатился бы вместе с ней — лог остался бы
     * (он уже сохранён отдельной REQUIRES_NEW-транзакцией), а рейтинг тихо не обновился бы.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recomputeProfile(Long userId) {
        List<SensitiveQueryLog> logs = sensitiveQueryLogRepository.findByUserId(userId);

        // Технические метки не считаются подтверждённой категорией — см. javadoc класса
        List<SensitiveQueryLog> confirmed = logs.stream()
                .filter(SensitiveQueryLog::isBlocked)
                .filter(l -> l.getCategory() != SensitiveContentCategory.CLASSIFICATION_FAILED
                        && l.getCategory() != SensitiveContentCategory.LLM_REFUSED)
                .toList();

        long totalTextQuestions = fortuneRepository.countByUserId(userId) + dreamReadingRepository.countByUserId(userId);

        Map<SensitiveContentCategory, Long> categoryCounts = new EnumMap<>(SensitiveContentCategory.class);
        for (SensitiveQueryLog l : confirmed) {
            categoryCounts.merge(l.getCategory(), 1L, Long::sum);
        }

        SensitiveContentCategory dominantCategory = categoryCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        BigDecimal percentage = totalTextQuestions == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(confirmed.size())
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalTextQuestions), 2, RoundingMode.HALF_UP);

        boolean selfHarmOverride = categoryCounts.containsKey(SensitiveContentCategory.SELF_HARM_SUICIDE);
        RiskLevel riskLevel = selfHarmOverride ? RiskLevel.RED : riskLevelFromPercentage(percentage);

        String categoryCountsJson = serializeCategoryCounts(categoryCounts);

        UserSensitivityProfile profile = userSensitivityProfileRepository.findById(userId)
                .orElse(UserSensitivityProfile.builder().userId(userId).build());
        profile.setTotalTextQuestions((int) totalTextQuestions);
        profile.setTotalSensitiveCount(confirmed.size());
        profile.setCategoryCounts(categoryCountsJson);
        profile.setSensitivePercentage(percentage);
        profile.setDominantCategory(dominantCategory);
        profile.setRiskLevel(riskLevel);
        userSensitivityProfileRepository.save(profile);

        log.info("Профиль рейтинга пересчитан: userId={}, percentage={}, riskLevel={}, dominant={}",
                userId, percentage, riskLevel, dominantCategory);
    }

    /** Полный пересчёт по всем пользователям, у которых есть хоть одна запись — после бэкафилла. */
    @Transactional
    public void recomputeAllProfiles() {
        List<Long> userIds = sensitiveQueryLogRepository.findDistinctUserIds();
        log.info("Пересчёт профилей рейтинга для {} пользователей после бэкафилла", userIds.size());
        userIds.forEach(this::recomputeProfile);
    }

    private RiskLevel riskLevelFromPercentage(BigDecimal percentage) {
        if (percentage.compareTo(RED_THRESHOLD_PERCENT) > 0) return RiskLevel.RED;
        if (percentage.compareTo(YELLOW_THRESHOLD_PERCENT) > 0) return RiskLevel.YELLOW;
        return RiskLevel.GREEN;
    }

    private String serializeCategoryCounts(Map<SensitiveContentCategory, Long> categoryCounts) {
        try {
            Map<String, Long> asStrings = new java.util.LinkedHashMap<>();
            categoryCounts.forEach((k, v) -> asStrings.put(k.name(), v));
            return objectMapper.writeValueAsString(asStrings);
        } catch (JsonProcessingException e) {
            log.warn("Не удалось сериализовать category_counts: {}", e.getMessage());
            return "{}";
        }
    }
}
