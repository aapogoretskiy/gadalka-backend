package ru.sapa.gadalka_backend.service.interpretation.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.api.dto.card.CardDto;
import ru.sapa.gadalka_backend.service.interpretation.AiInterpretationService;

import java.util.List;

@Slf4j
@Service("mock")
@RequiredArgsConstructor
public class MockAiInterpretationService implements AiInterpretationService {

    @Override
    public String interpret(List<CardDto> cards, String question) {
        StringBuilder builder = new StringBuilder();

        builder.append("Вопрос: \"").append(question).append("\". ");
        builder.append("Ваш расклад показывает: ");

        for (CardDto card : cards) {
            builder.append(card.getCardPosition())
                    .append(" - ")
                    .append(card.getName())
                    .append(". ");
        }

        builder.append("Карты намекают на важные изменения в вашей жизни.");

        return builder.toString();
    }

    @Override
    public String getProvider() {
        return "mock";
    }
}
