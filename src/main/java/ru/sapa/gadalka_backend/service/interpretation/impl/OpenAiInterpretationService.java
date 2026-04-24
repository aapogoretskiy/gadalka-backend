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
import ru.sapa.gadalka_backend.api.dto.card.CardPosition;
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
    public InterpretationResult interpret(List<CardDto> cards, String question, String category) {
        String categoryContext = resolveCategoryContext(category);

        String generalInterpretation = callAi(buildGeneralPrompt(cards, question, categoryContext),
                "Ты мистический таролог. Интерпретируй расклад таро очень кратко — не более 3-4 предложений суммарно. " +
                "Пиши атмосферно, строго в контексте вопроса пользователя. Не используй markdown или другие спецсимволы. " +
                "Называй позиции карт только по-русски: Прошлое, Настоящее, Будущее — никогда не пиши PAST, PRESENT, FUTURE. " +
                "Никаких длинных объяснений — только суть.");

        List<CardDto> cardsWithInterpretation = cards.stream()
                .map(card -> {
                    String cardInterpretation = callAi(buildCardPrompt(card, question, categoryContext),
                            "Ты мистический таролог. Дай очень краткую интерпретацию одной карты таро — 1-2 предложения. " +
                            "Строго в контексте вопроса пользователя. Не используй markdown или другие спецсимволы. " +
                            "Называй позицию карты только по-русски: Прошлое, Настоящее или Будущее.");
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

    private String buildGeneralPrompt(List<CardDto> cards, String question, String categoryContext) {
        StringBuilder sb = new StringBuilder();
        if (categoryContext != null) {
            sb.append("Сфера вопроса: ").append(categoryContext).append(". Сделай акцент именно на этой сфере.\n\n");
        }
        sb.append("Вопрос пользователя: ").append(question).append("\n\n");
        sb.append("Карты расклада:\n");
        for (CardDto card : cards) {
            sb.append(translatePosition(card.getCardPosition())).append(": ").append(card.getName()).append("\n");
        }
        sb.append("\nДай единую общую интерпретацию расклада в контексте вопроса. Не описывай карты по отдельности.");
        return sb.toString();
    }

    private String buildCardPrompt(CardDto card, String question, String categoryContext) {
        StringBuilder sb = new StringBuilder();
        if (categoryContext != null) {
            sb.append("Сфера вопроса: ").append(categoryContext).append(".\n");
        }
        sb.append("Вопрос пользователя: ").append(question).append("\n\n");
        sb.append("Карта: ").append(card.getName())
          .append(" в позиции «").append(translatePosition(card.getCardPosition())).append("».\n");
        sb.append("Дай краткую интерпретацию этой карты в контексте вопроса (1-2 предложения).");
        return sb.toString();
    }

    private String translatePosition(CardPosition position) {
        if (position == null) return "";
        return switch (position) {
            case PAST -> "Прошлое";
            case PRESENT -> "Настоящее";
            case FUTURE -> "Будущее";
        };
    }

    private String resolveCategoryContext(String category) {
        if (category == null || category.isBlank()) return null;
        return switch (category.toLowerCase()) {
            case "love"   -> "Любовь и отношения";
            case "money"  -> "Финансы и деньги";
            case "work"   -> "Работа и карьера";
            case "life"   -> "Жизненная ситуация";
            case "health" -> "Здоровье";
            default       -> null;
        };
    }

    private String callAi(String userPrompt, String systemPrompt) {
        log.debug("Отправляем запрос к OpenRouter AI, модель='{}', промпт: {}",
                aiModel, userPrompt.length() > 100 ? userPrompt.substring(0, 100) + "…" : userPrompt);

        AiRequest request = new AiRequest(
                aiModel,
                List.of(
                        new AiMessage("system", systemPrompt),
                        new AiMessage("user", userPrompt)
                )
        );

        AiResponse response;
        try {
            response = openRouterAiClient.post()
                    .body(BodyInserters.fromValue(request))
                    .retrieve()
                    .bodyToMono(AiResponse.class)
                    .block();
        } catch (Exception ex) {
            log.error("Ошибка HTTP-запроса к OpenRouter AI (модель='{}'): {}", aiModel, ex.getMessage(), ex);
            throw ex;
        }

        if (response == null) {
            log.warn("OpenRouter AI вернул пустой ответ (null) для модели '{}'", aiModel);
            return StringUtils.EMPTY;
        }

        List<AiResponse.Choice> choices = response.getChoices();
        if (CollectionUtils.isEmpty(choices)) {
            log.warn("OpenRouter AI вернул ответ без вариантов (choices пуст) для модели '{}'", aiModel);
            return StringUtils.EMPTY;
        }

        String content = choices.get(0).getMessage().getContent();
        log.debug("Получен ответ от OpenRouter AI, длина: {} символов", content != null ? content.length() : 0);
        return content;
    }
}
