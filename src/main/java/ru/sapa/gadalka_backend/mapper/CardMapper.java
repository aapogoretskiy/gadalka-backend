package ru.sapa.gadalka_backend.mapper;

import org.springframework.stereotype.Component;
import ru.sapa.gadalka_backend.api.dto.card.DailyCardResponse;
import ru.sapa.gadalka_backend.domain.Card;
import ru.sapa.gadalka_backend.domain.DailyCard;

import java.util.Objects;

@Component
public class CardMapper {

    public DailyCardResponse toDto(DailyCard dailyCard) {
        Card card = dailyCard.getCard();
        if (Objects.isNull(card)) {
            throw new RuntimeException(String.format("Cannot find card in daily card model by id: %s and for user id: %s",
                    dailyCard.getId(), dailyCard.getUserId()));
        }
        return new DailyCardResponse(dailyCard.getId(),
                card.getName(),
                card.getMeaning(),
                card.getAdvice(),
                card.getImageUrl(),
                dailyCard.getDate());
    }
}
