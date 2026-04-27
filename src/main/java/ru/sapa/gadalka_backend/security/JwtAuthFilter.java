package ru.sapa.gadalka_backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.sapa.gadalka_backend.repository.UserRepository;
import ru.sapa.gadalka_backend.service.JwtService;

import java.io.IOException;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    /** Пути, не требующие авторизации */
    private static final String[] PUBLIC_PATHS = {
            "/api/auth/",
            "/api/health",
            "/swagger-ui",
            "/v3/api-docs",
            "/actuator"
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring(7);
            try {
                Long userId = jwtService.getUserIdFromToken(token);
                userRepository.findById(userId).ifPresentOrElse(
                        user -> {
                            request.setAttribute("user", user);
                            log.debug("JWT аутентификация успешна: userId={}", user.getId());
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

        filterChain.doFilter(request, response);
    }

    private boolean requiresAuth(String uri) {
        for (String pub : PUBLIC_PATHS) {
            if (uri.startsWith(pub)) return false;
        }
        return true;
    }
}
