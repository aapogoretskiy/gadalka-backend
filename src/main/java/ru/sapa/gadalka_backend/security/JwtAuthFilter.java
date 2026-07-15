package ru.sapa.gadalka_backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.repository.UserRepository;
import ru.sapa.gadalka_backend.service.JwtService;
import ru.sapa.gadalka_backend.repository.UserVisitRepository;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import  ru.sapa.gadalka_backend.domain.UserVisit;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserVisitRepository userVisitRepository;

    /** Пути, не требующие авторизации */
    private static final String[] PUBLIC_PATHS = {
            "/api/auth/",
            "/api/health",
            "/swagger-ui",
            "/v3/api-docs",
            "/actuator",
            "/api/v1/payments/products",
            "/api/v1/payments/config",
            "/api/v1/subscriptions/plans",
            "/api/v1/payments/robokassa/pay/",
            "/api/v1/payments/yookassa/webhook",
            "/api/v1/payments/robokassa/webhook",
            "/api/v1/payments/robokassa/fail",
            "/api/admin/auth"
    };

    private static final long LAST_ACTIVE_UPDATE_INTERVAL_MINUTES = 5;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (request.getRequestURI().startsWith("/api/admin/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring(7);
            try {
                Long userId = jwtService.getUserIdFromToken(token);
                userRepository.findById(userId).ifPresentOrElse(
                        user -> {
                            request.setAttribute("user", user);
                            log.debug("JWT аутентификация успешна: userId={}", user.getId());
                            updateLastActiveAt(user);
                        },
                        () -> log.warn("JWT валиден, но пользователь с id={} не найден в БД", userId)
                );
            } catch (Exception ex) {
                log.warn("Невалидный JWT токен [{} {}]: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
            }
        }

        // Если путь защищён и пользователь не аутентифицирован — возвращаем 401
        if (requiresAuth(request.getRequestURI()) && request.getAttribute("user") == null) {
            log.warn("Отклонён неаутентифицированный запрос [{} {}]", request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"status\":401,\"message\":\"Требуется авторизация\",\"path\":\"%s\",\"timestamp\":\"%s\"}"
                            .formatted(request.getRequestURI(), LocalDateTime.now())
            );
            return;
        }

        User user = (User) request.getAttribute("user");
        if (user != null && user.isBanned()) {
            log.warn("Заблокированный пользователь пытается получить доступ: userId={}", user.getId());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"status\":403,\"message\":\"Аккаунт заблокирован\",\"path\":\"%s\",\"timestamp\":\"%s\"}"
                            .formatted(request.getRequestURI(), LocalDateTime.now())
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Обновляет время последней активности пользователя и фиксирует посещение.
     * Не чаще раза в 5 минут — чтобы не делать лишний UPDATE на каждый запрос.
     * При каждом новом "сеансе" (прошло > 5 минут):
     *   — инкрементируется visit_count в users
     *   — создаётся запись в user_visits для аналитики повторных визитов по датам
     */
    private void updateLastActiveAt(User user) {
        OffsetDateTime now = OffsetDateTime.now();
        if (user.getLastActiveAt() == null ||
                user.getLastActiveAt().isBefore(now.minusMinutes(LAST_ACTIVE_UPDATE_INTERVAL_MINUTES))) {
            user.setLastActiveAt(now);
            user.setVisitCount(user.getVisitCount() + 1);
            userRepository.save(user);

            // Запись лога посещения для аналитики по периодам
            try {
                userVisitRepository.save(UserVisit.builder()
                        .userId(user.getId())
                        .visitedAt(now)
                        .build());
            } catch (Exception e) {
                // Не критично — основная логика (JWT/сессия) не должна падать из-за аналитики
                log.warn("Не удалось записать посещение: userId={}, error={}", user.getId(), e.getMessage());
            }
        }
    }

    private boolean requiresAuth(String uri) {
        for (String pub : PUBLIC_PATHS) {
            if (uri.startsWith(pub)) return false;
        }
        return true;
    }
}
