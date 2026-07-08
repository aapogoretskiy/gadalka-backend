package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.domain.SystemConfig;
import ru.sapa.gadalka_backend.repository.SystemConfigRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository repository;

    public String getValue(String key) {
        return repository.findByKey(key)
                .map(SystemConfig::getValue)
                .orElse(null);
    }

    /**
     * Читает значение как целое число. Если ключ не найден или значение
     * не парсится как int — возвращает defaultValue (защита от падения,
     * если миграция не накатилась или значение испортили вручную).
     */
    public int getIntValue(String key, int defaultValue) {
        String raw = getValue(key);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Читает значение как булево. Понимает "true"/"false" (без учёта регистра).
     * Если ключ не найден или значение не распознано — возвращает defaultValue.
     */
    public boolean getBooleanValue(String key, boolean defaultValue) {
        String raw = getValue(key);
        if (raw == null) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(raw.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw.trim())) {
            return false;
        }
        return defaultValue;
    }

    /**
     * Время последнего изменения значения по ключу. Используется, например,
     * чтобы понять, когда именно фича была помечена «Новинка» — это позволяет
     * фронтенду показывать точку-уведомление заново, если админ переставил
     * отметку на другую фичу уже после того, как пользователь предыдущую видел.
     * Возвращает null, если ключа нет.
     */
    public LocalDateTime getUpdatedAt(String key) {
        return repository.findByKey(key)
                .map(SystemConfig::getUpdatedAt)
                .orElse(null);
    }

    /**
     * Записывает значение по ключу (upsert): обновляет существующую запись
     * или создаёт новую, если ключа ещё нет в system_config.
     */
    @Transactional
    public void setValue(String key, String value) {
        SystemConfig config = repository.findByKey(key).orElseGet(() -> {
            SystemConfig fresh = new SystemConfig();
            fresh.setKey(key);
            fresh.setCreatedAt(LocalDateTime.now());
            return fresh;
        });
        config.setValue(value);
        config.setUpdatedAt(LocalDateTime.now());
        repository.save(config);
    }
}
