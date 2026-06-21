package ru.sapa.gadalka_backend.api.dto.preset;

import lombok.Builder;
import lombok.Getter;
import ru.sapa.gadalka_backend.domain.QuestionPreset;

/**
 * Один заготовленный вопрос внутри категории.
 */
@Getter
@Builder
public class QuestionPresetDto {

    private Long id;
    private String questionText;

    public static QuestionPresetDto from(QuestionPreset preset) {
        return QuestionPresetDto.builder()
                .id(preset.getId())
                .questionText(preset.getQuestionText())
                .build();
    }
}
