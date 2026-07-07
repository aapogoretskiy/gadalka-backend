package ru.sapa.gadalka_backend.api.dto.admin;

import java.time.OffsetDateTime;

/**
 * Строка истории отправок во "Входящие" в админке (вкладка "Рассылка").
 * recipientsCount/readCount считаются одним агрегирующим запросом
 * (InboxMessageRepository#findMessageStats) — не N+1.
 */
public record InboxMessageStatsDto(
        Long id,
        String text,
        OffsetDateTime createdAt,
        long recipientsCount,
        long readCount
) {
}
