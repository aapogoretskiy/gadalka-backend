package ru.sapa.gadalka_backend.service.interpretation;

import ru.sapa.gadalka_backend.api.dto.card.CardDto;
import ru.sapa.gadalka_backend.api.dto.compatibility.CompatibilityCategoryScore;
import ru.sapa.gadalka_backend.api.dto.compatibility.CompatibilityRequest;

import java.util.List;

public interface AiInterpretationService {
    InterpretationResult interpret(List<CardDto> cards, String question);

    /**
     * Генерирует текстовую интерпретацию совместимости.
     * Числовые показатели рассчитываются нумерологически и передаются готовыми,
     * AI создаёт только атмосферный нарративный текст.
     */
    String interpretCompatibility(List<CompatibilityRequest.PersonInput> persons,
                                  int overallScore,
                                  List<CompatibilityCategoryScore> categories);

    String getProvider();
}
