package ru.sapa.gadalka_backend.api.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
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

    public AiRequest(String model, List<AiMessage> messages, Integer maxTokens) {
        this.model = model;
        this.messages = messages != null ? messages : new ArrayList<>();
        this.maxTokens = maxTokens;
    }
}
