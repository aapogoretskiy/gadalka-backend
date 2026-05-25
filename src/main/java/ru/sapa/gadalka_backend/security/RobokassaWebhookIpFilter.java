package ru.sapa.gadalka_backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Фильтр безопасности для webhook-эндпоинта Robokassa (ResultURL).
 * <p>
 * У Robokassa фиксированные IP-адреса (не диапазоны), поэтому фильтр проще,
 * чем аналогичный для ЮKassa.
 * <p>
 * Документация: https://docs.robokassa.ru/ru/notifications-and-redirects
 * Официальные IP: 185.59.216.65, 185.59.217.65
 */
@Slf4j
@Component
public class RobokassaWebhookIpFilter extends OncePerRequestFilter {

    private static final String WEBHOOK_PATH = "/api/v1/payments/robokassa/webhook";

    /**
     * Официальные IP-адреса Robokassa для ResultURL.
     * Актуально на момент написания — при необходимости обновить по документации.
     */
    private static final Set<String> ROBOKASSA_IPS = Set.of(
            "185.59.216.65",
            "185.59.217.65"
    );

    @Value("${robokassa.webhook.ip-filter-enabled:true}")
    private boolean ipFilterEnabled;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!WEBHOOK_PATH.equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!ipFilterEnabled) {
            log.warn("IP-фильтрация Robokassa отключена — все запросы на webhook принимаются");
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);

        if (!ROBOKASSA_IPS.contains(clientIp)) {
            log.warn("Заблокирован webhook Robokassa от неизвестного IP: {} [{} {}]",
                    clientIp, request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"status\":403,\"message\":\"Forbidden\",\"timestamp\":\"%s\"}"
                            .formatted(LocalDateTime.now())
            );
            return;
        }

        log.debug("Webhook Robokassa принят с IP: {}", clientIp);
        filterChain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
