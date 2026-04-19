package ru.sapa.gadalka_backend.service.interpretation.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import ru.sapa.gadalka_backend.api.dto.ai.AiMessage;
import ru.sapa.gadalka_backend.api.dto.ai.AiRequest;
import ru.sapa.gadalka_backend.api.dto.ai.AiResponse;
import ru.sapa.gadalka_backend.api.dto.card.CardDto;
import ru.sapa.gadalka_backend.service.interpretation.AiInterpretationService;

import java.util.List;

@Slf4j
@Service("openrouter")
@RequiredArgsConstructor
public class OpenAiInterpretationService implements AiInterpretationService {

    private final WebClient openRouterAiClient;

    @Value("${openrouter.model}")
    private String aiModel;

    @Override
    public String interpret(List<CardDto> cards, String question) {
        String prompt = buildPrompt(cards, question);
        log.info("prompt: {}", prompt);

        AiRequest request = new AiRequest(
                aiModel,
                List.of(
                        new AiMessage("system",
                                "Ты мистический таролог. Интерпретируй расклад таро кратко, красиво и атмосферно, " +
                                        "строго в контексте вопроса пользователя. Не используй markdown или другие спецсимволы"),
                        new AiMessage("user", prompt)
                )
        );

        AiResponse response = openRouterAiClient.post()
                .body(BodyInserters.fromValue(request))
                .retrieve()
                .bodyToMono(AiResponse.class)
                .block();

        if (response == null) {
            return StringUtils.EMPTY;
        }

        List<AiResponse.Choice> choices = response.getChoices();
        if (CollectionUtils.isEmpty(choices)) {
            return StringUtils.EMPTY;
        }

        return choices.get(0).getMessage().getContent();
    }

    @Override
    public String getProvider() {
        return "openrouter";
    }

    private String buildPrompt(List<CardDto> cards, String question) {
        StringBuilder promptBuilder = new StringBuilder();

        promptBuilder.append("Вопрос пользователя: ").append(question).append("\n\n");
        promptBuilder.append("Пользователь сделал расклад таро.\n");
        promptBuilder.append("Карты:\n");

        for (CardDto card : cards) {
            promptBuilder.append(card.getCardPosition())
                    .append(": ")
                    .append(card.getName())
                    .append("\n");
        }

        promptBuilder.append("\nСделай мистическую интерпретацию этого расклада строго в контексте заданного вопроса. ")
                .append("Каждую карту свяжи с вопросом напрямую.");

        return promptBuilder.toString();
    }
}
