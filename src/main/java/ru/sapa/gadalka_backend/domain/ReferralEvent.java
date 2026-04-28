package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;
import ru.sapa.gadalka_backend.domain.type.ReferralEventType;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Реферальное событие — фиксирует каждый переход по реферальной ссылке.
 * <p>
 * Одна запись = один факт:
 * <ul>
 *   <li>BOT_ENTRY  — бот получил /start CODE (пользователь кликнул по ссылке)</li>
 *   <li>APP_OPEN   — Mini App открылся с start_param=CODE в initData</li>
 * </ul>
 */
@Entity
@Table(
    name = "referral_events",
    indexes = {
        @Index(name = "idx_referral_events_code",        columnList = "referral_code"),
        @Index(name = "idx_referral_events_telegram_id", columnList = "telegram_id"),
        @Index(name = "idx_referral_events_created_at",  columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReferralEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Реферальный код, например "telegram_channel1" или "tiktok_video1". */
    @Column(name = "referral_code", nullable = false, length = 255)
    private String referralCode;

    /** Telegram ID пользователя, перешедшего по ссылке. */
    @Column(name = "telegram_id", nullable = false)
    private Long telegramId;

    /**
     * Внутренний ID пользователя в нашей БД.
     * Заполняется при APP_OPEN (когда пользователь авторизуется в Mini App).
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * Был ли пользователь новым в момент авторизации.
     * Заполняется только для события APP_OPEN.
     */
    @Column(name = "is_new_user")
    private Boolean isNewUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private ReferralEventType eventType;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (Objects.isNull(this.createdAt)) {
            this.createdAt = OffsetDateTime.now();
        }
    }
}
