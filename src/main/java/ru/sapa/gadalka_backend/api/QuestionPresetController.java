package ru.sapa.gadalka_backend.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sapa.gadalka_backend.api.dto.preset.QuestionCategoryDto;
import ru.sapa.gadalka_backend.service.QuestionPresetService;

import java.util.List;

/**
 * Пресеты вопросов для экрана "О чём спросить карты?".
 * Справочный эндпоинт — не зависит от пользователя, но как и /api/themes
 * требует JWT-авторизацию (путь не входит в JwtAuthFilter.PUBLIC_PATHS).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/question-presets")
@Tag(name = "Пресеты вопросов", description = "Заготовленные вопросы по категориям (Любовь, Деньги, Работа, Ситуация, Здоровье)")
public class QuestionPresetController {

    private final QuestionPresetService questionPresetService;

    @GetMapping
    @Operation(summary = "Список категорий вопросов с заготовленными вопросами")
    public List<QuestionCategoryDto> getQuestionPresets() {
        return questionPresetService.getCategoriesWithPresets();
    }
}
