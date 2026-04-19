package ru.sapa.gadalka_backend.service.interpretation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.api.dto.card.CardDto;
import ru.sapa.gadalka_backend.api.dto.compatibility.CompatibilityCategoryScore;
import ru.sapa.gadalka_backend.api.dto.compatibility.CompatibilityRequest;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiInterpretationManager {
    private final Map<String, AiInterpretationService> strategies;

    public InterpretationResult interpret(String provider, List<CardDto> cards, String question) {
        return getService(provider).interpret(cards, question);
    }

    public String interpretCompatibility(String provider,
                                         List<CompatibilityRequest.PersonInput> persons,
                                         int overallScore,
                                         List<CompatibilityCategoryScore> categories) {
        return getService(provider).interpretCompatibility(persons, overallScore, categories);
    }

    private AiInterpretationService getService(String provider) {
        AiInterpretationService service = strategies.get(provider);
        if (service == null) {
            throw new IllegalArgumentException("Unknown AI provider: " + provider);
        }
        return service;
    }
}
