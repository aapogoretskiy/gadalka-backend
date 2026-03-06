package ru.sapa.gadalka_backend.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sapa.gadalka_backend.api.dto.TelegramAuthResponse;
import ru.sapa.gadalka_backend.service.TelegramAuthService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final TelegramAuthService telegramAuthService;

    @PostMapping("/telegram")
    public TelegramAuthResponse authenticate(@RequestHeader("Authorization") String initData) {
        return telegramAuthService.authenticate(initData);
    }
}
