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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Фильтр безопасности для webhook-эндпоинта ЮKassa.
 * <p>
 * Проблема: эндпоинт /yookassa/webhook публичный (без JWT), значит
 * лдюбой желающий может слать туа фиктивные уведомления об "успешных" платежах.
 * <p>
 * Решение: ЮKassa документирует фиксированные диапазоны IP, с которых она шлёт
 * webhook'и. Мы разрешаем только их.
 * <p>
 * Важно: мы стоим за nginx → реальный IP лежит в X-Real-IP, а не в remoteAddr.
 * nginx должен быть настроен: proxy_set_header X-Real-IP $remote_addr;
 * <p>
 * Документация IP-диапазонов: https://yookassa.ru/developers/using-api/webhooks#ip
 */
@Slf4j
@Component
public class YooKassaWebhookIpFilter extends OncePerRequestFilter {

    private static final String WEBHOOK_PATH = "/api/v1/payments/yookassa/webhook";

    /**
     * Официальные IP-диапазоны ЮKassa (CIDR-нотация).
     * Актуально на момент написания — при необходимости обновить по документации.
     */
    private static final List<String> YOOKASSA_IP_RANGES = List.of(
            "185.71.76.0/27",
            "185.71.77.0/27",
            "77.75.153.0/25",
            "77.75.154.128/25",
            "2a02:5180::/32"      // IPv6
    );

    /**
     * Флаг включения IP-фильтрации.
     * В dev-окружении можно отключить: YOOKASSA_WEBHOOK_IP_FILTER_ENABLED=false
     * В продакшне всегда должен быть true.
     */
    @Value("${yookassa.webhook.ip-filter-enabled:true}")
    private boolean ipFilterEnabled;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        // Фильтруем только webhook-путь, остальные запросы пропускаем без проверки
        if (!WEBHOOK_PATH.equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!ipFilterEnabled) {
            log.warn("IP-фильтрация ЮKassa отключена — все запросы на webhook принимаются");
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);

        if (!isYooKassaIp(clientIp)) {
            log.warn("Заблокирован webhook от неизвестного IP: {} [{} {}]",
                    clientIp, request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"status\":403,\"message\":\"Forbidden\",\"timestamp\":\"%s\"}"
                            .formatted(LocalDateTime.now())
            );
            return;
        }

        log.debug("Webhook от ЮKassa принят с IP: {}", clientIp);
        filterChain.doFilter(request, response);
    }

    /**
     * Определяет реальный IP клиента с учётом reverse proxy.
     * <p>
     * Порядок приоритета:
     * 1. X-Real-IP — nginx выставляет это поле в $remote_addr (IP ближайшего клиента/прокси)
     * 2. X-Forwarded-For — может содержать цепочку через запятую: "client, proxy1, proxy2"
     *    берём первый элемент (оригинальный клиент)
     * 3. request.getRemoteAddr() — TCP-уровень, в случае nginx будет 127.0.0.1
     */
    private String resolveClientIp(HttpServletRequest request) {
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // "185.71.76.5, 10.0.0.1, ..." → берём первый
            return xForwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }

    /**
     * Проверяет, входит ли IP в один из разрешённых диапазонов ЮKassa.
     */
    private boolean isYooKassaIp(String ipStr) {
        InetAddress ip;
        try {
            ip = InetAddress.getByName(ipStr);
        } catch (UnknownHostException e) {
            log.warn("Не удалось распарсить IP: '{}'", ipStr);
            return false;
        }

        for (String range : YOOKASSA_IP_RANGES) {
            try {
                if (isInCidrRange(ip, range)) {
                    return true;
                }
            } catch (UnknownHostException e) {
                log.error("Некорректный CIDR в конфигурации: {}", range, e);
            }
        }
        return false;
    }

    /**
     * CIDR-матчинг без сторонних библиотек.
     * <p>
     * Принцип: берём первые prefixLength бит у IP и у сетевого адреса.
     * Если они совпадают — IP входит в этот диапазон.
     * <p>
     * Пример: 185.71.76.5 vs 185.71.76.0/27
     * /27 → маска первых 27 бит: 185.71.76.0 (11111111.11111111.11111111.11100000)
     * 185.71.76.5 & маска = 185.71.76.0 → совпадает → входит в диапазон ✓
     */
    private boolean isInCidrRange(InetAddress ip, String cidr) throws UnknownHostException {
        String[] parts = cidr.split("/");
        InetAddress network = InetAddress.getByName(parts[0]);
        int prefixLength = Integer.parseInt(parts[1]);

        byte[] ipBytes      = ip.getAddress();
        byte[] networkBytes = network.getAddress();

        // IPv4 (4 байта) и IPv6 (16 байт) не могут совпасть
        if (ipBytes.length != networkBytes.length) return false;

        int remainingBits = prefixLength;
        for (int i = 0; i < ipBytes.length && remainingBits > 0; i++) {
            int bits = Math.min(8, remainingBits);
            // Маска для текущего байта: например bits=5 → 11111000 → 0xF8
            int mask = (0xFF << (8 - bits)) & 0xFF;
            if ((ipBytes[i] & mask) != (networkBytes[i] & mask)) {
                return false;
            }
            remainingBits -= bits;
        }
        return true;
    }
}
