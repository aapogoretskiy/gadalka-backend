package ru.sapa.gadalka_backend.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import ru.sapa.gadalka_backend.api.dto.telegram.TelegramUserDto;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.repository.UserRepository;
import ru.sapa.gadalka_backend.service.NotificationAccessService;
import ru.sapa.gadalka_backend.service.ReferralService;
import ru.sapa.gadalka_backend.service.UserService;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Me", description = "Контроллер проверки функционала приложения")
public class MeController extends BaseController {

    private static final String BOT_USERNAME = "magicliora_bot";

    private final UserService userService;
    private final ReferralService referralService;
    private final UserRepository userRepository;
    private final NotificationAccessService notificationAccessService;

    /**
     * POST /api/me/accept-terms
     *
     * <p>Фиксирует согласие с офертой и политикой конфиденциальности на welcome-экране
     * онбординга. Раньше согласие писалось только при создании профиля
     * ({@code UserProfileService#createProfile}), но профиль стал опциональным —
     * а юридическая фиксация нужна ДО первого действия в приложении.
     * Идемпотентен: повторный вызов не перезаписывает первоначальную дату согласия.
     */
    @PostMapping("/me/accept-terms")
    @Operation(summary = "Зафиксировать согласие с офертой и политикой конфиденциальности")
    @Transactional
    public ResponseEntity<Map<String, Boolean>> acceptTerms(@RequestBody AcceptTermsRequest body,
                                                            HttpServletRequest request) {
        User user = resolveUser(request);
        if (user.getTermsAcceptedAt() == null) {
            user.setTermsAcceptedAt(OffsetDateTime.now());
            user.setTermsVersion(body.termsVersion());
            userRepository.save(user);
        }
        return ResponseEntity.ok(Map.of("accepted", true));
    }

    public record AcceptTermsRequest(String termsVersion) {}

    /**
     * POST /api/me/notifications-allowed
     *
     * <p>Пользователь нажал «Разрешить» в баннере уведомлений и Telegram подтвердил
     * доступ. Сам Telegram присылает боту служебное write_access_allowed только когда
     * разрешение реально меняется — если человек когда-то жал /start, право писать уже
     * есть, служебного сообщения нет, и бэкенд об этом никогда не узнавал. Из-за этого
     * баннер возвращался после каждой перезагрузки.
     *
     * <p>Проверяем доступ фактом отправки приветствия (см. NotificationAccessService),
     * а не на слово клиенту: {@code allowed=false} значит, что бот писать не может —
     * фронт покажет подсказку, а флаг в БД останется честным.
     */
    @PostMapping("/me/notifications-allowed")
    @Operation(summary = "Подтвердить, что бот может писать пользователю")
    public ResponseEntity<Map<String, Boolean>> confirmNotificationsAllowed(HttpServletRequest request) {
        User user = resolveUser(request);
        boolean allowed = notificationAccessService.confirmWriteAccess(user);
        return ResponseEntity.ok(Map.of("allowed", allowed));
    }

    @GetMapping("/me")
    @Operation(summary = "Получение пользователя по bearer токену")
    public TelegramUserDto meByTelegram(HttpServletRequest request) {
        return userService.getTelegramUser(resolveUser(request));
    }

    /**
     * GET /api/me/referral
     *
     * <p>Возвращает реферальную ссылку текущего пользователя.
     *
     * <p>Используем формат {@code ?startapp=}, а не {@code ?start=}:
     * <ul>
     *   <li>{@code ?start=CODE} — отправляет боту команду /start CODE (BOT_ENTRY), но НЕ
     *       передаёт {@code start_param} в initData Mini App.</li>
     *   <li>{@code ?startapp=CODE} — открывает Mini App напрямую и передаёт CODE как
     *       {@code start_param} в initData, что позволяет серверу зафиксировать реферала.</li>
     * </ul>
     */
    @GetMapping("/me/referral")
    @Operation(summary = "Получить реферальную ссылку текущего пользователя")
    public ResponseEntity<Map<String, String>> getReferralLink(HttpServletRequest request) {
        User user = resolveUser(request);
        String code = referralService.buildReferralCode(user.getTelegramId());
        String link = "https://t.me/" + BOT_USERNAME + "?startapp=" + code;
        return ResponseEntity.ok(Map.of(
                "code", code,
                "link", link
        ));
    }
}
