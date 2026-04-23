package ru.sapa.gadalka_backend.api.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.sapa.gadalka_backend.domain.User;

import java.io.IOException;

@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();

        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String clientIp = resolveClientIp(request);
        String fullPath = queryString != null ? uri + "?" + queryString : uri;

        log.info("→ {} {} [IP: {}]", method, fullPath, clientIp);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();

            Object userAttr = request.getAttribute("user");
            String userInfo = userAttr instanceof User user
                    ? "userId=" + user.getId()
                    : "не авторизован";

            if (status >= 500) {
                log.error("← {} {} → {} [{}мс, {}]", method, fullPath, status, duration, userInfo);
            } else if (status >= 400) {
                log.warn("← {} {} → {} [{}мс, {}]", method, fullPath, status, duration, userInfo);
            } else {
                log.info("← {} {} → {} [{}мс, {}]", method, fullPath, status, duration, userInfo);
            }
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
