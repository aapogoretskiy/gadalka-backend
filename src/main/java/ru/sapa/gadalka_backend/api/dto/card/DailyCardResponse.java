package ru.sapa.gadalka_backend.api.dto.card;

import java.time.LocalDate;

public record DailyCardResponse(
        Long cardId,
        String name,
        String meaning,
        String advice,
        String imageUrl,
        LocalDate date
) {
}
