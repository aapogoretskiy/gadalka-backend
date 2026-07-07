package ru.sapa.gadalka_backend.api.dto.inbox;

import java.time.OffsetDateTime;

/**
 * Одно сообщение во "Входящих" глазами конкретного пользователя (GET /api/inbox).
 * {@code read} вычисляется на бэке из {@code recipient.readAt != null} — фронту
 * не нужно знать про readAt как таковой, только факт прочтения.
 */
public record InboxMessageDto(
        Long id,
        String text,
        OffsetDateTime createdAt,
        boolean read
) {
}
