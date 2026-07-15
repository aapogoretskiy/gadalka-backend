package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * План подписки из каталога — то, что админ настраивает в админ-панели,
 * а пользователь видит во вкладке «Подписки».
 * <p>
 * Квоты плана лежат в {@link SubscriptionPlanQuota} (отдельная таблица).
 * Цены хранятся как в {@link PaymentProduct}: rub в копейках, stars в штуках.
 */
@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Отображаемое название, например «Базовая» */
    @Column(name = "name", nullable = false)
    private String name;

    /** Цена в копейках (для Robokassa) */
    @Column(name = "price_rub", nullable = false)
    private Integer priceRub;

    /** Цена в звёздах Telegram */
    @Column(name = "price_stars", nullable = false)
    private Integer priceStars;

    /** Срок действия подписки в днях */
    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    /** Неактивные планы не отображаются в каталоге и недоступны для покупки */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    /** Порядок отображения в каталоге (меньше = выше) */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
