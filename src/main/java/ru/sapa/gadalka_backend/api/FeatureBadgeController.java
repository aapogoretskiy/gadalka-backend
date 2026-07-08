package ru.sapa.gadalka_backend.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sapa.gadalka_backend.api.dto.admin.FeatureBadgesDto;
import ru.sapa.gadalka_backend.service.FeatureBadgeService;

/**
 * Публичная (для авторизованных пользователей мини-аппа) точка чтения отметок
 * «Новинка» / «Хит» на платных функциях. По аналогии с {@link FeatureCostController} —
 * не требует admin JWT, только обычную авторизацию пользователя через JwtAuthFilter.
 *
 * <p>Используется навигацией (жёлтые точки на вкладках) и карточками функций
 * (рамка/шильдик «Новинка» или «Хит»), чтобы не хардкодить эти отметки во фронтенде —
 * админ включает и выключает их из админ-панели без деплоя.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "FeatureBadges", description = "Отметки «Новинка» / «Хит» на платных функциях")
public class FeatureBadgeController {

    private final FeatureBadgeService featureBadgeService;

    @GetMapping("/feature-badges")
    @Operation(summary = "Текущие отметки «Новинка»/«Хит» по всем платным функциям")
    public FeatureBadgesDto getFeatureBadges() {
        return featureBadgeService.getAllBadges();
    }
}
