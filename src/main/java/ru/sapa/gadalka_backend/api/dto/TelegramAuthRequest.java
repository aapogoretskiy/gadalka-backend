package ru.sapa.gadalka_backend.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TelegramAuthRequest {
    @NotBlank
    private String initData;
}
