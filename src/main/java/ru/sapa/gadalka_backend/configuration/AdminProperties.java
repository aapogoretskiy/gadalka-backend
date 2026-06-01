package ru.sapa.gadalka_backend.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

/**
 * Конфигурация белого листа администраторов.
 *
 * <p>Список telegram_id администраторов задаётся через ENV-переменную,
 * а не хранится в БД — это исключает возможность получить права
 * администратора через манипуляции с данными в базе.
 *
 * <p>Пример в .env: ADMIN_TELEGRAM_IDS=123456789,987654321,111111111
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "admin")
public class AdminProperties {

    /**
     * Список Telegram ID пользователей, имеющих доступ к админ-панели.
     * Максимум 3 человека по договорённости.
     */
    private List<Long> telegramIds = List.of();

    /** Проверяет, является ли указанный telegramId администратором */
    public boolean isAdmin(Long telegramId) {
        return telegramIds.contains(telegramId);
    }
}
