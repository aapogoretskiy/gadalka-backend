package ru.sapa.gadalka_backend.service.interpretation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.api.dto.card.CardDto;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiInterpretationManager {
    private final Map<String, AiInterpretationService> strategies;

    public String interpret(String provider, List<CardDto> cards) {
        AiInterpretationService interpretationService = strategies.get(provider);
        if (interpretationService == null) {
            throw new IllegalArgumentException("Unknown AI provider: " + provider);
        }
        return interpretationService.interpret(cards);
    }
}
