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
    private int readingBalance;
    /** true если пользователь зарегистрировался впервые (для приветственного сообщения на фронте) */
    private boolean isNewUser;
    /**
     * true если пользователь уже принял оферту и политику конфиденциальности.
     * Фронт использует это как гейт онбординга: не принял — welcome-экран, принял — главная.
     */
    private boolean termsAccepted;
}
