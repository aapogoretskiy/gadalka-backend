package ru.sapa.gadalka_backend.api.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AiRequest {
    private String model;
    private List<AiMessage> messages = new ArrayList<>();
}
