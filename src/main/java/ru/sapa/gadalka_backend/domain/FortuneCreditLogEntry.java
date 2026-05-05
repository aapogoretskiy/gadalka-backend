package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;
import ru.sapa.gadalka_backend.domain.type.CreditTransactionReason;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;

import java.time.OffsetDateTime;

/**
 * Запись в истории движения кредитов (гаданий).
 * Append-only лог — записи никогда не удаляются и не изменяются.
 * <p>
 * delta > 0 — начисление (покупка, бонус)
 * delta < 0 — списание (использование функции)
 */
@Entity
@Table(name = "fortune_credit_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FortuneCreditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Положительное — начисление, отрицательное — списание */
    @Column(name = "delta", nullable = false)
    private Integer delta;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 100)
    private CreditTransactionReason reason;

    /** Заполняется при reason=PAYMENT */
    @Column(name = "payment_id")
    private Long paymentId;

    /** Заполняется при reason=FEATURE_SPEND: какая функция была использована */
    @Enumerated(EnumType.STRING)
    @Column(name = "feature_type", length = 100)
    private DiaryFeatureType featureType;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }
}
