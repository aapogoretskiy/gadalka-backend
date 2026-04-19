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
import ru.sapa.gadalka_backend.api.dto.compatibility.CompatibilityCategoryScore;
import ru.sapa.gadalka_backend.api.dto.compatibility.CompatibilityRequest;
import ru.sapa.gadalka_backend.service.interpretation.AiInterpretationService;
import ru.sapa.gadalka_backend.service.interpretation.InterpretationResult;

import java.util.List;

@Slf4j
@Service("openrouter")
@RequiredArgsConstructor
public class OpenAiInterpretationService implements AiInterpretationService {

    private final WebClient openRouterAiClient;

    @Value("${openrouter.model}")
    private String aiModel;

    @Override
    public InterpretationResult interpret(List<CardDto> cards, String question) {
        String generalInterpretation = callAi(buildGeneralPrompt(cards, question),
                "Ты мистический таролог. Интерпретируй расклад таро очень кратко — не более 3-4 предложений суммарно. " +
                "Пиши атмосферно, строго в контексте вопроса пользователя. Не используй markdown или другие спецсимволы. " +
                "Никаких длинных объяснений — только суть.");

        List<CardDto> cardsWithInterpretation = cards.stream()
                .map(card -> {
                    String cardInterpretation = callAi(buildCardPrompt(card, question),
                            "Ты мистический таролог. Дай очень краткую интерпретацию одной карты таро — 1-2 предложения. " +
                            "Строго в контексте вопроса пользователя. Не используй markdown или другие спецсимволы.");
                    return CardDto.builder()
                            .id(card.getId())
                            .name(card.getName())
                            .meaning(card.getMeaning())
                            .cardPosition(card.getCardPosition())
                            .interpretation(cardInterpretation)
                            .build();
                })
                .toList();

        return new InterpretationResult(generalInterpretation, cardsWithInterpretation);
    }

    @Override
    public String interpretCompatibility(List<CompatibilityRequest.PersonInput> persons,
                                         int overallScore,
                                         List<CompatibilityCategoryScore> categories) {
        return callAi(
                buildCompatibilityPrompt(persons, overallScore, categories),
                "Ты мистический нумеролог. Дай короткую атмосферную интерпретацию совместимости двух людей — " +
                "не более 2-3 предложений. Опирайся на числа и имена. " +
                "Не повторяй проценты и цифры из запроса. Не используй markdown или другие спецсимволы."
        );
    }

    @Override
    public String getProvider() {
        return "openrouter";
    }

    // -------------------------------------------------------------------------

    private String buildCompatibilityPrompt(List<CompatibilityRequest.PersonInput> persons,
                                            int overallScore,
                                            List<CompatibilityCategoryScore> categories) {
        StringBuilder sb = new StringBuilder();
        sb.append("Нумерологический анализ совместимости:\n");
        for (CompatibilityRequest.PersonInput p : persons) {
            sb.append("- ").append(p.getName())
              .append(", дата рождения: ").append(p.getBirthDate()).append("\n");
        }
        sb.append("\nОбщая совместимость: ").append(overallScore).append("%\n");
        sb.append("Детализация:\n");
        for (CompatibilityCategoryScore cat : categories) {
            sb.append("  ").append(cat.getName()).append(": ").append(cat.getScore()).append("%\n");
        }
        sb.append("\nНапиши короткую мистическую интерпретацию этого союза.");
        return sb.toString();
    }

    private String buildGeneralPrompt(List<CardDto> cards, String question) {
        StringBuilder sb = new StringBuilder();
        sb.append("Вопрос пользователя: ").append(question).append("\n\n");
        sb.append("Карты расклада:\n");
        for (CardDto card : cards) {
            sb.append(card.getCardPosition()).append(": ").append(card.getName()).append("\n");
        }
        sb.append("\nДай единую общую интерпретацию расклада в контексте вопроса. Не описывай карты по отдельности.");
        return sb.toString();
    }

    private String buildCardPrompt(CardDto card, String question) {
        return "Вопрос пользователя: " + question + "\n\n" +
               "Карта: " + card.getName() + " в позиции «" + card.getCardPosition() + "».\n" +
               "Дай краткую интерпретацию этой карты в контексте вопроса (1-2 предложения).";
    }

    private String callAi(String userPrompt, String systemPrompt) {
        log.info("AI prompt: {}", userPrompt);

        AiRequest request = new AiRequest(
                aiModel,
                List.of(
                        new AiMessage("system", systemPrompt),
                        new AiMessage("user", userPrompt)
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
}
