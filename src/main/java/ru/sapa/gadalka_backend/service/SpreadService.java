package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.api.dto.card.CardDto;
import ru.sapa.gadalka_backend.api.dto.card.CardPosition;
import ru.sapa.gadalka_backend.domain.Card;
import ru.sapa.gadalka_backend.domain.CardDeckTheme;
import ru.sapa.gadalka_backend.domain.type.SpreadType;
import ru.sapa.gadalka_backend.mapper.CardMapper;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpreadService {

    // ── Три карты ────────────────────────────────────────────────────────────
    private static final List<CardPosition> THREE_CARD_POSITIONS = List.of(
            CardPosition.PAST,
            CardPosition.PRESENT,
            CardPosition.FUTURE
    );

    // ── Подкова (7 карт) ────────────────────────────────────────────────────
    private static final List<CardPosition> HORSESHOE_POSITIONS = List.of(
            CardPosition.HORSESHOE_PAST,
            CardPosition.HORSESHOE_PRESENT,
            CardPosition.HORSESHOE_HIDDEN,
            CardPosition.HORSESHOE_OBSTACLES,
            CardPosition.HORSESHOE_EXTERNAL,
            CardPosition.HORSESHOE_ADVICE,
            CardPosition.HORSESHOE_OUTCOME
    );

    // ── Кельтский крест (10 карт) ────────────────────────────────────────────
    private static final List<CardPosition> CELTIC_CROSS_POSITIONS = List.of(
            CardPosition.CELTIC_HEART,
            CardPosition.CELTIC_CROSS,
            CardPosition.CELTIC_FOUNDATION,
            CardPosition.CELTIC_PAST,
            CardPosition.CELTIC_POSSIBLE_FUTURE,
            CardPosition.CELTIC_NEAR_FUTURE,
            CardPosition.CELTIC_SELF,
            CardPosition.CELTIC_EXTERNAL,
            CardPosition.CELTIC_HOPES_FEARS,
            CardPosition.CELTIC_OUTCOME
    );

    private final CardMapper cardMapper;

    /**
     * Назначает позиции картам согласно типу расклада с учётом активной темы.
     *
     * @param theme активная тема пользователя (может быть null)
     */
    public List<CardDto> assignCardPosition(List<Card> cards, SpreadType spreadType, CardDeckTheme theme) {
        List<CardPosition> positions = getPositions(spreadType);
        if (cards.size() != positions.size()) {
            log.warn("Несоответствие: карт={}, позиций={} для расклада {}", cards.size(), positions.size(), spreadType);
        }
        List<CardDto> cardDtoList = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            CardDto cardDto = cardMapper.toDto(cards.get(i), theme);
            cardDto.setCardPosition(positions.get(i));
            cardDtoList.add(cardDto);
        }
        return cardDtoList;
    }

    /**
     * Возвращает количество карт для данного типа расклада.
     */
    public int getCardCount(SpreadType spreadType) {
        return getPositions(spreadType).size();
    }

    private List<CardPosition> getPositions(SpreadType spreadType) {
        return switch (spreadType) {
            case THREE_CARD    -> THREE_CARD_POSITIONS;
            case HORSESHOE     -> HORSESHOE_POSITIONS;
            case CELTIC_CROSS  -> CELTIC_CROSS_POSITIONS;
        };
    }
}
