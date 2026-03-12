package ru.sapa.gadalka_backend.service.interpretation;

import ru.sapa.gadalka_backend.api.dto.card.CardDto;

import java.util.List;

public interface AiInterpretationService {
    String interpret(List<CardDto> cards);
    String getProvider();
}
