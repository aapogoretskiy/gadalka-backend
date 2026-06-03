package ru.sapa.gadalka_backend.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import ru.sapa.gadalka_backend.api.dto.telegram.TelegramUserDto;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.service.ReferralService;
import ru.sapa.gadalka_backend.service.UserService;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Me", description = "Контроллер проверки функционала приложения")
public class MeController extends BaseController {

    private static final String BOT_USERNAME = "magicliora_bot";

    private final UserService userService;
    private final ReferralService referralService;

    @GetMapping("/me")
    @Operation(summary = "Получение пользователя по bearer токену")
    public TelegramUserDto meByTelegram(HttpServletRequest request) {
        return userService.getTelegramUser(resolveUser(request));
    }

    /**
     * GET /api/me/referral
     *
     * <p>Возвращает реферальную ссылку текущего пользователя.
     * Формат ссылки: {@code https://t.me/magicliora_bot?start=ref_<telegramId>}
     */
    @GetMapping("/me/referral")
    @Operation(summary = "Получить реферальную ссылку текущего пользователя")
    public ResponseEntity<Map<String, String>> getReferralLink(HttpServletRequest request) {
        User user = resolveUser(request);
        String code = referralService.buildReferralCode(user.getTelegramId());
        String link = "https://t.me/" + BOT_USERNAME + "?start=" + code;
        return ResponseEntity.ok(Map.of(
                "code", code,
                "link", link
        ));
    }
}
