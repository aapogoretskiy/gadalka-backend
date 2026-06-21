package ru.sapa.gadalka_backend.api.dto.preset;

import lombok.Builder;
import lombok.Getter;
import ru.sapa.gadalka_backend.domain.QuestionCategory;

import java.util.List;

/**
 * Ответ на GET /api/question-presets — категория вопроса со списком готовых вопросов.
 * code совпадает со значениями, разрешёнными в FortuneRequest.category (love, money, work, life, health).
 */
@Getter
@Builder
public class QuestionCategoryDto {

    private Long id;
    private String code;
    private String name;
    private List<QuestionPresetDto> presets;

    public static QuestionCategoryDto from(QuestionCategory category, List<QuestionPresetDto> presets) {
        return QuestionCategoryDto.builder()
                .id(category.getId())
                .code(category.getCode())
                .name(category.getName())
                .presets(presets)
                .build();
    }
}
