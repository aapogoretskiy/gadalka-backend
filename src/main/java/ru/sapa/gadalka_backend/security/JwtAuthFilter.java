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

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorizationHeader.substring(7);
        try {
            Long userId = jwtService.getUserIdFromToken(token);
            userRepository.findById(userId).ifPresentOrElse(
                    user -> {
                        request.setAttribute("user", user);
                        log.debug("JWT аутентификация успешна: userId={}", user.getId()); //@TODO как будто очень тяжкая херня каждый раз ходить в БД...
                    },
                    () -> log.warn("JWT валиден, но пользователь с id={} не найден в БД", userId)
            );
        } catch (Exception ex) {
            log.warn("Невалидный JWT токен [{} {}]: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
