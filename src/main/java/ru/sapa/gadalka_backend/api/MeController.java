package ru.sapa.gadalka_backend.api;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sapa.gadalka_backend.api.dto.TelegramUserDto;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.service.UserService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MeController {

    private final UserService userService;

    @GetMapping("/me")
    public TelegramUserDto meByTelegram(HttpServletRequest request) {
        return userService.getTelegramUser((User) request.getAttribute("user"));
    }
}
