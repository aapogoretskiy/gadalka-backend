package ru.sapa.gadalka_backend.api.dto.feedback;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FeedbackRequest(
        @NotBlank(message = "Описание проблемы не может быть пустым")
        @Size(min = 10, max = 2000, message = "Описание должно содержать от 10 до 2000 символов")
        String description
) {}
