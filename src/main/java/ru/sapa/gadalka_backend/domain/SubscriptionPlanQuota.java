package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;
import ru.sapa.gadalka_backend.domain.type.QuotaPeriod;

/**
 * Квота плана подписки: сколько раз можно использовать конкретную фичу.
 * Это ШАБЛОН — при покупке подписки квоты копируются в {@link SubscriptionQuota}
 * (снапшот), поэтому редактирование плана не влияет на уже купленные подписки.
 */
@Entity
@Table(name = "subscription_plan_quotas")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlanQuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_type", nullable = false, length = 50)
    private DiaryFeatureType featureType;

    /** Сколько использований даёт квота */
    @Column(name = "quota_count", nullable = false)
    private Integer quotaCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "quota_period", nullable = false, length = 20)
    private QuotaPeriod quotaPeriod;

    /**
     * «Безлимит» для пользователя. Технически quota_count — скрытый дневной
     * анти-абьюз лимит (period всегда DAILY), но UI показывает «Безлимит» без чисел.
     */
    @Column(name = "is_unlimited", nullable = false)
    private Boolean isUnlimited;
}
