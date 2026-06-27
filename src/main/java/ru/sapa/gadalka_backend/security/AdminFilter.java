package ru.sapa.gadalka_backend.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.sapa.gadalka_backend.configuration.AdminProperties;
import ru.sapa.gadalka_backend.service.JwtService;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * Фильтр безопасности для всех запросов к /api/admin/**.
 *
 * <p>Алгоритм проверки (двойная защита):
 * <ol>
 *   <li>Читает Admin JWT из httpOnly-куки "admin_token"</li>
 *   <li>Проверяет подпись токена и claim role (ADMIN или MODERATOR)</li>
 *   <li>Проверяет что telegramId из токена до сих пор есть в соответствующем ENV-whitelist</li>
 *   <li>Для MODERATOR: блокирует любые запросы кроме GET (модератор — только чтение)</li>
 * </ol>
 *
 * <p>Эндпоинты /api/admin/auth/** исключены — они публичны (там происходит сам вход).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminFilter extends OncePerRequestFilter {

    public static final String ADMIN_COOKIE_NAME = "admin_token";

    private final JwtService jwtService;
    private final AdminProperties adminProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/admin/") || uri.startsWith("/api/admin/auth")) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = extractCookie(request, ADMIN_COOKIE_NAME);
        if (token == null) {
            rejectUnauthorized(response, request.getRequestURI(), "Отсутствует admin_token cookie");
            return;
        }
        try {
            Claims claims = jwtService.getAdminClaims(token);

            String role = claims.get("role", String.class);
            Long telegramId = claims.get("telegramId", Long.class);

            if ("ADMIN".equals(role)) {
                // Полный доступ — проверяем whitelist администраторов
                if (!adminProperties.isAdmin(telegramId)) {
                    log.warn("Admin JWT валиден, но telegramId={} исключён из whitelist администраторов", telegramId);
                    rejectForbidden(response, uri, "Доступ отозван");
                    return;
                }
                log.debug("Admin-запрос авторизован: telegramId={}, uri={}", telegramId, uri);

            } else if ("MODERATOR".equals(role)) {
                if (!adminProperties.isModerator(telegramId)) {
                    log.warn("Moderator JWT валиден, но telegramId={} исключён из whitelist модераторов", telegramId);
                    rejectForbidden(response, uri, "Доступ отозван");
                    return;
                }
                if (!"GET".equalsIgnoreCase(request.getMethod())) {
                    log.warn("Модератор telegramId={} попытался выполнить {} {}", telegramId, request.getMethod(), uri);
                    rejectForbidden(response, uri, "Недостаточно прав: модератор имеет доступ только для чтения");
                    return;
                }
                log.debug("Moderator-запрос авторизован: telegramId={}, uri={}", telegramId, uri);

            } else {
                rejectForbidden(response, uri, "Недостаточно прав");
                return;
            }

            request.setAttribute("adminTelegramId", telegramId);
            request.setAttribute("adminRole", role);

        } catch (Exception ex) {
            log.warn("Невалидный Admin JWT [{} {}]: {}", request.getMethod(), uri, ex.getMessage());
            rejectUnauthorized(response, uri, "Невалидный токен");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private void rejectUnauthorized(HttpServletResponse response, String path, String message) throws IOException {
        log.warn("Отклонён неаутентифицированный admin-запрос: {} — {}", path, message);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"status\":401,\"message\":\"%s\",\"path\":\"%s\",\"timestamp\":\"%s\"}"
                        .formatted(message, path, LocalDateTime.now())
        );
    }

    private void rejectForbidden(HttpServletResponse response, String path, String message) throws IOException {
        log.warn("Отклонён несанкционированный admin-запрос: {} — {}", path, message);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"status\":403,\"message\":\"%s\",\"path\":\"%s\",\"timestamp\":\"%s\"}"
                        .formatted(message, path, LocalDateTime.now())
        );
    }
}
