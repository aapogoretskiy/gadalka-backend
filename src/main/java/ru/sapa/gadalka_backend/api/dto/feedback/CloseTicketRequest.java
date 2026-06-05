package ru.sapa.gadalka_backend.api.dto.feedback;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CloseTicketRequest(
        @Min(value = 0, message = "creditsToGift не может быть отрицательным")
        @Max(value = 100, message = "creditsToGift не может превышать 100")
        Integer creditsToGift
) {}
