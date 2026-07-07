package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Связка "сообщение → получатель" с индивидуальным статусом прочтения.
 * <p>
 * {@code readAt == null} — не прочитано. Прочтение фиксируется разом для всех сообщений
 * пользователя при заходе на вкладку "Входящие" (см. InboxService.markAllRead) —
 * отдельного read-статуса на каждое сообщение в UI нет, так решили сознательно для простоты.
 */
@Entity
@Table(
        name = "inbox_message_recipients",
        indexes = {
                @Index(name = "idx_inbox_recipients_user_unread", columnList = "user_id, read_at"),
                @Index(name = "idx_inbox_recipients_message", columnList = "message_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboxMessageRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "read_at")
    private OffsetDateTime readAt;
}
