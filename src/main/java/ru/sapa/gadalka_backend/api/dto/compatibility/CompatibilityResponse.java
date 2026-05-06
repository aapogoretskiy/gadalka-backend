package ru.sapa.gadalka_backend.api.dto.compatibility;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CompatibilityResponse {
    private final List<CompatibilityRequest.PersonInput> persons;
    /** Общий процент совместимости (0–100), рассчитывается нумерологически */
    private final int compatibilityScore;
    /** Текстовый лейбл по уровню совместимости, например «Идеальная пара» */
    private final String label;
    /** ID расклада — нужен фронту для вызова /unlock */
    private final Long id;
    /** true — пользователь уже разблокировал полный анализ */
    private final boolean unlocked;
    /** AI-интерпретация совместимости. null в превью-режиме */
    private final String interpretation;
    /** Детализация по категориям. null в превью-режиме */
    private final List<CompatibilityCategoryScore> categories;
}
