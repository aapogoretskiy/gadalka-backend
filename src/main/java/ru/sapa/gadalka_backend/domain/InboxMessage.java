package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Сообщение администратора, отправленное во "Входящие" внутри приложения (не через Telegram).
 * <p>
 * Гарантированный канал доставки — не зависит от {@code User.notificationsAllowed}:
 * MiniApp доступен пользователю напрямую в любом случае, в отличие от проактивных
 * сообщений бота. См. миграцию V60.
 */
@Entity
@Table(name = "inbox_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /** Telegram ID администратора, запустившего рассылку (для аудита). */
    @Column(name = "created_by_admin_telegram_id")
    private Long createdByAdminTelegramId;

    @PrePersist
    void prePersist() {
        if (Objects.isNull(this.createdAt)) {
            this.createdAt = OffsetDateTime.now();
        }
    }
}
