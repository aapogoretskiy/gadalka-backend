package ru.sapa.gadalka_backend.api.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AiMessage {
    private String role;
    private String content;
}
