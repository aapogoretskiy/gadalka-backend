package ru.sapa.gadalka_backend.configuration;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * Конфигурация белого листа администраторов и модераторов.
 *
 * <p>Список telegram_id задаётся через ENV-переменные,
 * а не хранится в БД — это исключает возможность получить права
 * через манипуляции с данными в базе.
 *
 * <p>Пример в .env:
 * ADMIN_TELEGRAM_IDS=123456789,987654321
 * ADMIN_MODERATOR_TELEGRAM_IDS=111111111,222222222
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "admin")
public class AdminProperties {

    /**
     * Список Telegram ID пользователей, имеющих полный доступ к админ-панели.
     */
    private String telegramIds = StringUtils.EMPTY;

    /**
     * Список Telegram ID модераторов — могут просматривать все отчёты,
     * но не могут вносить изменения (только GET-запросы).
     */
    private String moderatorTelegramIds = StringUtils.EMPTY;

    /** Проверяет, является ли указанный telegramId администратором */
    public boolean isAdmin(Long telegramId) {
        return parseLongs(telegramIds).contains(telegramId);
    }

    /** Проверяет, является ли указанный telegramId модератором */
    public boolean isModerator(Long telegramId) {
        return parseLongs(moderatorTelegramIds).contains(telegramId);
    }

    private List<Long> parseLongs(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(Long::parseLong)
                .toList();
    }
}
