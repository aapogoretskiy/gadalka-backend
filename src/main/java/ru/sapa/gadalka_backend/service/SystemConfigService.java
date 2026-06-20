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
