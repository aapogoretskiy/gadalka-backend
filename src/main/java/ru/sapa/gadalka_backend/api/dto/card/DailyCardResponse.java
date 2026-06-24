package ru.sapa.gadalka_backend.api.dto.card;

import java.time.LocalDate;
import java.util.List;

public record DailyCardResponse(
        Long cardId,
        String name,
        String meaning,
        String advice,
        String imageUrl,
        LocalDate date,
        String insightTitle,
        String descriptionParagraph1,
        String descriptionParagraph2,
        List<String> keywords
) {
}
