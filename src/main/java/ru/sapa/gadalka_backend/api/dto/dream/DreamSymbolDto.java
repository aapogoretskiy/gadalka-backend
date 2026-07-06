package ru.sapa.gadalka_backend.api.dto.dream;

/** Символ-чип на экране ввода Сонника (только активные, отсортированы по sort_order). */
public record DreamSymbolDto(
        Long id,
        String emoji,
        String name
) {}
