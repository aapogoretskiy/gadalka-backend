package ru.sapa.gadalka_backend.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.service.UserService;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MeController {

    private final UserService userService;

    @GetMapping("/me/by-telegram/{telegramId}")
    public Map<Long, User> meByTelegram(@PathVariable Long telegramId) {
        return Map.of(telegramId, userService.getUserByTelegramId(telegramId));
    }
}
