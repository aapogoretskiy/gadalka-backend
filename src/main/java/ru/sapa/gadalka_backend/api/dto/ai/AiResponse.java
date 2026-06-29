package ru.sapa.gadalka_backend.api.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

@Getter
public class AiResponse {

    private List<Choice> choices;

    @Getter
    public static class Choice {
        private Message message;
        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Getter
    public static class Message {
        private String content;
    }
}
