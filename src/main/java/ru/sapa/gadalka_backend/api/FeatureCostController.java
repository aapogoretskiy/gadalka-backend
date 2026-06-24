package ru.sapa.gadalka_backend.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sapa.gadalka_backend.api.dto.admin.FeatureCostsDto;
import ru.sapa.gadalka_backend.service.FeatureCostService;

/**
 * Публичная (для авторизованных пользователей мини-аппа) точка чтения стоимости
 * платных функций. В отличие от /api/admin/feature-costs, не требует admin JWT —
 * только обычную авторизацию пользователя через JwtAuthFilter.
 *
 * <p>Используется экранами выбора расклада, совместимости и нумерологии недели,
 * чтобы показывать пользователю актуальную цену до оплаты — раньше эти экраны
 * хранили цены захардкоженными в коде и расходились с реальной стоимостью списания
 * после того, как цену меняли через админ-панель.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "FeatureCosts", description = "Стоимость платных функций в знаках")
public class FeatureCostController {

    private final FeatureCostService featureCostService;

    @GetMapping("/feature-costs")
    @Operation(summary = "Текущая стоимость платных функций (расклады, совместимость, нумерология недели)")
    public FeatureCostsDto getFeatureCosts() {
        return featureCostService.getAllCosts();
    }
}
