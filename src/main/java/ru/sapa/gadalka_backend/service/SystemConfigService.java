package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.domain.SystemConfig;
import ru.sapa.gadalka_backend.repository.SystemConfigRepository;

@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository repository;

    public String getValue(String key) {
        return repository.findByKey(key)
                .map(SystemConfig::getValue)
                .orElse(null);
    }
}
