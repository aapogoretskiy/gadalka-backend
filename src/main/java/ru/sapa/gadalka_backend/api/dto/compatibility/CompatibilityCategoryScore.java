package ru.sapa.gadalka_backend.api.dto.compatibility;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CompatibilityCategoryScore {
    private String name;
    /** Процент совместимости по данной категории (0–100) */
    private int score;
}
