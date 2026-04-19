package ru.sapa.gadalka_backend.service.interpretation.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.api.dto.card.CardDto;
import ru.sapa.gadalka_backend.api.dto.compatibility.CompatibilityCategoryScore;
import ru.sapa.gadalka_backend.api.dto.compatibility.CompatibilityRequest;
import ru.sapa.gadalka_backend.service.interpretation.AiInterpretationService;
import ru.sapa.gadalka_backend.service.interpretation.InterpretationResult;

import java.util.List;

@Slf4j
@Service("mock")
@RequiredArgsConstructor
public class MockAiInterpretationService implements AiInterpretationService {

    @Override
    public InterpretationResult interpret(List<CardDto> cards, String question) {
        String generalInterpretation = "Карты намекают на важные изменения в вашей жизни. Следуйте интуиции.";

        List<CardDto> cardsWithInterpretation = cards.stream()
                .map(card -> CardDto.builder()
                        .id(card.getId())
                        .name(card.getName())
                        .meaning(card.getMeaning())
                        .cardPosition(card.getCardPosition())
                        .interpretation("Карта " + card.getName() + " в позиции «" + card.getCardPosition() + "» указывает на перемены.")
                        .build())
                .toList();

        return new InterpretationResult(generalInterpretation, cardsWithInterpretation);
    }

    @Override
    public String interpretCompatibility(List<CompatibilityRequest.PersonInput> persons,
                                         int overallScore,
                                         List<CompatibilityCategoryScore> categories) {
        String person1 = persons.get(0).getName();
        String person2 = persons.get(1).getName();
        return "Звёзды благосклонны к союзу " + person1 + " и " + person2 + ". " +
               "Числа судьбы говорят о глубокой внутренней связи. " +
               "Следуйте своей интуиции и доверяйте чувствам.";
    }

    @Override
    public String getProvider() {
        return "mock";
    }
}
