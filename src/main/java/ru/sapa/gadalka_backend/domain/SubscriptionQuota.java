package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;
import ru.sapa.gadalka_backend.domain.type.QuotaPeriod;

import java.time.LocalDate;

/**
 * Снапшот квоты купленной подписки + счётчик использования.
 * Создаётся при активации подписки копированием из {@link SubscriptionPlanQuota}.
 * <p>
 * DAILY-квоты: {@code usedCount} относится ко дню {@code usageDate} (по МСК).
 * Сброс ленивый — при первом обращении в новый день сервис обнуляет счётчик
 * и сдвигает дату, отдельный шедулер не нужен.
 * <p>
 * PER_PERIOD-квоты: {@code usageDate} = null, счётчик копится весь срок подписки.
 */
@Entity
@Table(name = "subscription_quotas")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionQuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_type", nullable = false, length = 50)
    private DiaryFeatureType featureType;

    /** Снапшот количества из плана на момент покупки */
    @Column(name = "quota_count", nullable = false)
    private Integer quotaCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "quota_period", nullable = false, length = 20)
    private QuotaPeriod quotaPeriod;

    @Column(name = "used_count", nullable = false)
    private Integer usedCount;

    /** День (МСК), к которому относится usedCount у DAILY-квот. NULL для PER_PERIOD */
    @Column(name = "usage_date")
    private LocalDate usageDate;

    /** Снапшот флага «безлимит» из плана (см. SubscriptionPlanQuota.isUnlimited) */
    @Column(name = "is_unlimited", nullable = false)
    private Boolean isUnlimited;
}
