package ru.sapa.gadalka_backend.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Me", description = "Контроллер проверки функционала приложения")
public class MeController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Получение пользователя по bearer токену")
    public TelegramUserDto meByTelegram(HttpServletRequest request) {
        return userService.getTelegramUser((User) request.getAttribute("user"));
    }
}
