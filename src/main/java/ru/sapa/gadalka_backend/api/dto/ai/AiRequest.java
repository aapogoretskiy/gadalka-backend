package ru.sapa.gadalka_backend.api.dto.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiRequest {

    private final String model;
    private final List<AiMessage> messages;

    /**
     * Максимальное количество токенов в ответе AI.
     * Сериализуется как "max_tokens" согласно OpenRouter/OpenAI API.
     * Ограничивает стоимость одного запроса и защищает от атак
     * направленных на генерацию огромных ответов.
     */
    @JsonProperty("max_tokens")
    private final Integer maxTokens;

    /**
     * Режим «токенов мыслей». Если null — поле в JSON не попадает вообще
     * (см. {@link JsonInclude}), и провайдер использует поведение по умолчанию.
     * Так запрос остаётся прежним для всех вызовов, где мы рассуждения не трогаем.
     */
    private final AiReasoning reasoning;

    public AiRequest(String model, List<AiMessage> messages, Integer maxTokens) {
        this(model, messages, maxTokens, null);
    }

    public AiRequest(String model, List<AiMessage> messages, Integer maxTokens, AiReasoning reasoning) {
        this.model = model;
        this.messages = messages != null ? messages : new ArrayList<>();
        this.maxTokens = maxTokens;
        this.reasoning = reasoning;
    }
}
