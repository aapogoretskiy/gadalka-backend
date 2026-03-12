package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.api.dto.card.CardDto;
import ru.sapa.gadalka_backend.api.dto.card.CardPosition;
import ru.sapa.gadalka_backend.domain.Card;
import ru.sapa.gadalka_backend.mapper.CardMapper;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpreadService {

    private static final List<CardPosition> CARD_POSITIONS = List.of(CardPosition.PAST,
            CardPosition.PRESENT,
            CardPosition.FUTURE);

    private final CardMapper cardMapper;

    public List<CardDto> assignCardPosition(List<Card> cards) {
        List<CardDto> cardDtoList = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            CardDto cardDto = cardMapper.toDto(cards.get(i));
            cardDto.setCardPosition(CARD_POSITIONS.get(i));
            cardDtoList.add(cardDto);
        }
        return cardDtoList;
    }
}
