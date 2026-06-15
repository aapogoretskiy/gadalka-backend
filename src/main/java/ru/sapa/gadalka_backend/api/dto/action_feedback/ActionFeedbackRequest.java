package ru.sapa.gadalka_backend.api.dto.action_feedback;

import jakarta.validation.constraints.NotNull;
import ru.sapa.gadalka_backend.domain.type.FeedbackRating;

/**
 * Тело запроса POST /api/action-feedback/{type}/{actionId}.
 *
 * @param rating  обязательная оценка (POSITIVE / NEGATIVE)
 * @param comment опциональный текст — учитывается только при NEGATIVE
 */
public record ActionFeedbackRequest(
        @NotNull(message = "rating обязателен")
        FeedbackRating rating,
        String comment
) {}
