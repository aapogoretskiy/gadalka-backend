package ru.sapa.gadalka_backend.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.sapa.gadalka_backend.api.dto.telegram.TelegramAuthResponse;
import ru.sapa.gadalka_backend.service.TelegramAuthService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Авторизация пользователей")
public class AuthController {

    private final TelegramAuthService telegramAuthService;

    @PostMapping("/telegram")
    @Operation(summary = "Авторизуем пользователя с помощью telegram initData")
    public TelegramAuthResponse authenticate(@RequestBody String initData) {
        return telegramAuthService.authenticate(initData);
    }
}
