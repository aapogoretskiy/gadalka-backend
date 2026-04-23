package ru.sapa.gadalka_backend.service.interpretation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.api.dto.card.CardDto;
import ru.sapa.gadalka_backend.api.dto.compatibility.CompatibilityCategoryScore;
import ru.sapa.gadalka_backend.api.dto.compatibility.CompatibilityRequest;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiInterpretationManager {
    private final Map<String, AiInterpretationService> strategies;

    public InterpretationResult interpret(String provider, List<CardDto> cards, String question) {
        log.debug("Интерпретация расклада таро через провайдер '{}', карт: {}", provider, cards.size());
        return getService(provider).interpret(cards, question);
    }

    public String interpretCompatibility(String provider,
                                         List<CompatibilityRequest.PersonInput> persons,
                                         int overallScore,
                                         List<CompatibilityCategoryScore> categories) {
        log.debug("Интерпретация совместимости через провайдер '{}', участников: {}", provider, persons.size());
        return getService(provider).interpretCompatibility(persons, overallScore, categories);
    }

    private AiInterpretationService getService(String provider) {
        AiInterpretationService service = strategies.get(provider);
        if (service == null) {
            log.error("Неизвестный AI-провайдер: '{}'. Доступные провайдеры: {}", provider, strategies.keySet());
            throw new IllegalArgumentException("Неизвестный AI-провайдер: " + provider);
        }
        return service;
    }
}
