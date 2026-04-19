package ru.sapa.gadalka_backend.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import ru.sapa.gadalka_backend.api.dto.compatibility.CompatibilityCategoryScore;

import java.util.List;

@Getter
@AllArgsConstructor
public class NumerologyCompatibilityResult {
    /** Общий процент совместимости (0–100) */
    private final int overallScore;
    private final List<CompatibilityCategoryScore> categories;
}
