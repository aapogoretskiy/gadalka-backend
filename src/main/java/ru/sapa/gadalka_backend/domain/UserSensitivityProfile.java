package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;
import ru.sapa.gadalka_backend.domain.type.RiskLevel;
import ru.sapa.gadalka_backend.domain.type.SensitiveContentCategory;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Агрегированный "рейтинг склонности к чувствительным вопросам" пользователя.
 * Пересчитывается инкрементально при каждом логировании в {@link SensitiveQueryLog},
 * а также целиком — после бэкафилла истории.
 *
 * <p>Знаменатель процента — только свободнотекстовые вопросы (гадания Таро + Сонник),
 * а не {@code User.totalActionsCount}: нумерология/гороскоп не дают пользователю
 * возможности задать чувствительный вопрос вообще, включение их в знаменатель
 * искусственно занижало бы процент.
 *
 * <p>{@code riskLevel} = RED принудительно (override), если среди категорий пользователя
 * встречается {@link SensitiveContentCategory#SELF_HARM_SUICIDE} — независимо от процента:
 * тут важно не пропустить единичный случай, а не усреднять статистику.
 */
@Entity
@Table(name = "user_sensitivity_profile")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSensitivityProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "total_text_questions", nullable = false)
    private int totalTextQuestions;

    @Column(name = "total_sensitive_count", nullable = false)
    private int totalSensitiveCount;

    /** JSON-сериализованная карта SensitiveContentCategory -> count */
    @Column(name = "category_counts", nullable = false, columnDefinition = "TEXT")
    private String categoryCounts;

    @Column(name = "sensitive_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal sensitivePercentage;

    @Enumerated(EnumType.STRING)
    @Column(name = "dominant_category", length = 100)
    private SensitiveContentCategory dominantCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }
}
