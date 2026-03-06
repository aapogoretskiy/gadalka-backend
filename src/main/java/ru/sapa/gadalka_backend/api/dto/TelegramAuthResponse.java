package ru.sapa.gadalka_backend.api.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramAuthResponse {
    private TelegramUserDto user;
    private String jwtToken;
}
