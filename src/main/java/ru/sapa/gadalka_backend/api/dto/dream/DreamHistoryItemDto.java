package ru.sapa.gadalka_backend.api.dto.dream;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Лёгкая карточка для блока «Недавние сны» на экране Сонника.
 * Полный разбор подгружается отдельно по GET /api/dreams/{id}.
 */
public record DreamHistoryItemDto(
        Long id,
        OffsetDateTime createdAt,
        List<String> titleSymbols
) {}
