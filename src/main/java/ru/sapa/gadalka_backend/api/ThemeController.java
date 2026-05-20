package ru.sapa.gadalka_backend.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.sapa.gadalka_backend.api.dto.theme.ThemeDto;
import ru.sapa.gadalka_backend.service.ThemeService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/themes")
@Tag(name = "Темы карт", description = "Магазин тем (колод) карт Таро")
public class ThemeController extends BaseController {

    private final ThemeService themeService;

    @GetMapping
    @Operation(summary = "Список всех тем с флагами owned/active для текущего пользователя")
    public List<ThemeDto> getThemes(HttpServletRequest request) {
        Long userId = resolveUser(request).getId();
        return themeService.getThemes(userId);
    }

    @PostMapping("/{themeId}/activate")
    @Operation(summary = "Активировать тему (сменить активную колоду)")
    public ResponseEntity<Void> activateTheme(@PathVariable Long themeId, HttpServletRequest request) {
        Long userId = resolveUser(request).getId();
        themeService.activateTheme(userId, themeId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{themeId}/purchase")
    @Operation(summary = "Купить тему за кредиты")
    public ResponseEntity<Void> purchaseTheme(@PathVariable Long themeId, HttpServletRequest request) {
        Long userId = resolveUser(request).getId();
        themeService.purchaseTheme(userId, themeId);
        return ResponseEntity.ok().build();
    }
}
