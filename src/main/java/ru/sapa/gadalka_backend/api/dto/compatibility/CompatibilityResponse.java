package ru.sapa.gadalka_backend.api.dto.compatibility;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class CompatibilityResponse {
    private List<CompatibilityRequest.PersonInput> persons;
    /** Общий процент совместимости (0–100), рассчитывается нумерологически */
    private int compatibilityScore;
    /** Текстовый лейбл по уровню совместимости, например «Идеальная пара» */
    private String label;
    /** AI-интерпретация совместимости */
    private String interpretation;
    /** Детализация по категориям: Эмоции, Интеллект, Ценности, Страсть */
    private List<CompatibilityCategoryScore> categories;
}
