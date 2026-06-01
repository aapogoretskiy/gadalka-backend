package ru.sapa.gadalka_backend.api;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.sapa.gadalka_backend.configuration.AdminProperties;
import ru.sapa.gadalka_backend.security.AdminFilter;
import ru.sapa.gadalka_backend.service.JwtService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Авторизация администраторов через Telegram Login Widget.
 *
 * <p>Telegram Widget после подтверждения пользователем отправляет на наш callback
 * набор параметров с HMAC-подписью (hash). Мы проверяем подпись — это гарантирует,
 * что данные пришли именно от Telegram, а не были подделаны.
 *
 * <p>После успешной проверки Admin JWT кладётся в httpOnly-куку — JavaScript
 * на странице не может её прочитать, что защищает от XSS-атак.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final JwtService jwtService;
    private final AdminProperties adminProperties;

    @Value("${telegram.bot.token}")
    private String botToken;

    /** TTL куки совпадает с TTL токена — 8 часов */
    private static final int COOKIE_MAX_AGE_SECONDS = 8 * 60 * 60;

    /**
     * POST /api/admin/auth/login
     *
     * <p>Принимает данные от Telegram Login Widget:
     * id, first_name, username, photo_url, auth_date, hash
     *
     * <p>Проверяет:
     * 1. HMAC-подпись
     * 2. auth_date не старше 10 минут
     * 3. id пользователя есть в whitelist администраторов
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> widgetData,
                                   HttpServletResponse response) {
        try {
            String receivedHash = widgetData.get("hash");
            if (receivedHash == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Отсутствует hash"));
            }
            if (!verifyTelegramHash(widgetData, receivedHash)) {
                log.warn("Неверная подпись Telegram Login Widget: data={}", widgetData);
                return ResponseEntity.status(403).body(Map.of("message", "Неверная подпись"));
            }
            long authDate = Long.parseLong(widgetData.get("auth_date"));
            long nowSeconds = System.currentTimeMillis() / 1000;
            if (nowSeconds - authDate > 600) {
                return ResponseEntity.status(403).body(Map.of("message", "Данные авторизации устарели"));
            }
            Long telegramId = Long.parseLong(widgetData.get("id"));
            // Проверяем whitelist
            if (!adminProperties.isAdmin(telegramId)) {
                log.warn("Попытка входа в админку от неизвестного пользователя: telegramId={}", telegramId);
                return ResponseEntity.status(403).body(Map.of("message", "Доступ запрещён"));
            }

            String adminToken = jwtService.generateAdminToken(telegramId);
            Cookie cookie = buildAdminCookie(adminToken);
            response.addCookie(cookie);

            log.info("Успешный вход в админку: telegramId={}", telegramId);
            return ResponseEntity.ok(Map.of("message", "Авторизация успешна"));

        } catch (Exception e) {
            log.error("Ошибка при авторизации в админке", e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Внутренняя ошибка"));
        }
    }

    /**
     * POST /api/admin/auth/logout
     * Сбрасывает httpOnly-куку (выставляет maxAge=0).
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        Cookie cookie = buildAdminCookie(StringUtils.EMPTY);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return ResponseEntity.ok(Map.of("message", "Выход выполнен"));
    }

    /**
     * GET /api/admin/auth/me
     * Проверяет наличие активной сессии — используется фронтендом при загрузке страницы.
     * AdminFilter пропускает /api/admin/auth/**, поэтому здесь ручная проверка куки.
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        String token = Arrays.stream(request.getCookies() != null ? request.getCookies() : new Cookie[0])
                .filter(c -> AdminFilter.ADMIN_COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);

        if (token == null || token.isBlank()) {
            return ResponseEntity.status(401).body(Map.of("authenticated", false));
        }
        try {
            var claims = jwtService.getAdminClaims(token);
            Long telegramId = claims.get("telegramId", Long.class);
            if (!adminProperties.isAdmin(telegramId)) {
                return ResponseEntity.status(403).body(Map.of("authenticated", false));
            }
            return ResponseEntity.ok(Map.of("authenticated", true, "telegramId", telegramId));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("authenticated", false));
        }
    }

    /**
     * Проверяет HMAC-подпись данных Telegram Login Widget.
     *
     * <p>Алгоритм (из официальной документации Telegram):
     * 1. Собираем строку из всех полей (кроме hash) в формате "key=value", отсортированных по ключу
     * 2. Вычисляем secret_key = HMAC-SHA256(bot_token, "WebAppData")
     * 3. Вычисляем HMAC-SHA256(data_check_string, secret_key)
     * 4. Сравниваем с полученным hash
     */
    private boolean verifyTelegramHash(Map<String, String> data, String receivedHash) throws Exception {
        // Собираем строку проверки (все поля кроме hash, отсортированные по ключу)
        String dataCheckString = data.entrySet().stream()
                .filter(e -> !"hash".equals(e.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("\n"));

        // secret_key = SHA256(bot_token)
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] secretKey = sha256.digest(botToken.getBytes(StandardCharsets.UTF_8));

        // HMAC-SHA256(data_check_string, secret_key)
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
        byte[] computedHash = hmac.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8));

        String computedHex = HexFormat.of().formatHex(computedHash);
        return MessageDigest.isEqual(computedHex.getBytes(), receivedHash.getBytes());
    }

    private Cookie buildAdminCookie(String value) {
        Cookie cookie = new Cookie(AdminFilter.ADMIN_COOKIE_NAME, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/api/admin");
        cookie.setMaxAge(COOKIE_MAX_AGE_SECONDS);
        cookie.setAttribute("SameSite", "Strict");
        return cookie;
    }
}
