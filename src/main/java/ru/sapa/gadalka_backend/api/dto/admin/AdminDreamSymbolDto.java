package ru.sapa.gadalka_backend.api.dto.admin;

/**
 * Символ сна для админ-панели (CRUD /api/admin/dream-symbols).
 * В отличие от пользовательского {@code DreamSymbolDto}, содержит служебные поля:
 * подсказку для промпта, порядок сортировки и флаг активности.
 */
public record AdminDreamSymbolDto(
        Long id,
        String emoji,
        String name,
        String promptHint,
        Integer sortOrder,
        Boolean isActive
) {}
