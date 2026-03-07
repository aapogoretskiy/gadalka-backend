package ru.sapa.gadalka_backend.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Health", description = "Проверка статусов приложения")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "Состояние приложения")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
