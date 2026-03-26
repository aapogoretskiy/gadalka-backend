package ru.sapa.gadalka_backend.mapper;

import org.springframework.stereotype.Component;
import ru.sapa.gadalka_backend.api.dto.card.CardDto;
import ru.sapa.gadalka_backend.api.dto.card.DailyCardResponse;
import ru.sapa.gadalka_backend.domain.Card;
import ru.sapa.gadalka_backend.domain.DailyCard;

import java.util.Objects;

@Component
public class CardMapper {

    public CardDto toDto(Card card) {
        return CardDto.builder()
                .id(card.getId())
                .name(card.getName())
                .meaning(card.getMeaning())
                .build();
    }

    public DailyCardResponse toDailyCardDto(DailyCard dailyCard) {
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
