package ru.sapa.gadalka_backend.api.dto.telegram;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramAuthResponse {
    private TelegramUserDto user;
    private String jwtToken;
    private boolean fortuneUsed;
}
