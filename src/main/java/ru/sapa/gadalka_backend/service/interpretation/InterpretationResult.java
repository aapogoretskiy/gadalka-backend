package ru.sapa.gadalka_backend.service.interpretation;

import lombok.AllArgsConstructor;
import lombok.Getter;
import ru.sapa.gadalka_backend.api.dto.card.CardDto;

import java.util.List;

@Getter
@AllArgsConstructor
public class InterpretationResult {
    private final String generalInterpretation;
    private final List<CardDto> cards;
}
