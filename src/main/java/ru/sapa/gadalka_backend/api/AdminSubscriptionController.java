package ru.sapa.gadalka_backend.api;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sapa.gadalka_backend.api.dto.admin.AdminSubscriptionPlanDto;
import ru.sapa.gadalka_backend.service.SubscriptionPlanAdminService;

import java.util.List;
import java.util.Map;

/**
 * Админ-API планов подписки. Путь /api/admin/** защищён {@code AdminFilter}
 * (JWT + whitelist ADMIN_TELEGRAM_IDS), модераторам доступны только GET.
 * <p>
 * Флоу админа: создать план → указать название, цену ₽ (фронт подсказывает
 * цену в Stars по курсу starsRateKopecks), срок, квоты → «Сохранить подписку» →
 * план появляется во вкладке «Подписки» мини-аппа (пока — только у админов).
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/subscription-plans")
@RequiredArgsConstructor
public class AdminSubscriptionController {

    private final SubscriptionPlanAdminService adminService;

    /**
     * GET /api/admin/subscription-plans
     * Все планы (включая неактивные) + курс Stars для подсказки цены.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getPlans(HttpServletRequest request) {
        List<AdminSubscriptionPlanDto> plans = adminService.getAllPlans();
        return ResponseEntity.ok(Map.of(
                "plans", plans,
                "starsRateKopecks", adminService.getStarsRateKopecks()
        ));
    }

    /**
     * POST /api/admin/subscription-plans — создать план с квотами.
     */
    @PostMapping
    public ResponseEntity<?> createPlan(@RequestBody AdminSubscriptionPlanDto body, HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("adminTelegramId");
        log.info("Admin {} создаёт план подписки: '{}'", adminId, body.name());
        try {
            return ResponseEntity.ok(adminService.createPlan(body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * PUT /api/admin/subscription-plans/{id} — обновить план (квоты заменяются целиком).
     * Не влияет на уже купленные подписки — их квоты заснапшочены при активации.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updatePlan(@PathVariable Long id,
                                        @RequestBody AdminSubscriptionPlanDto body,
                                        HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("adminTelegramId");
        log.info("Admin {} обновляет план подписки id={}", adminId, id);
        try {
            return ResponseEntity.ok(adminService.updatePlan(id, body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * PUT /api/admin/subscription-plans/stars-rate — обновить курс «копеек за звезду».
     */
    @PutMapping("/stars-rate")
    public ResponseEntity<?> updateStarsRate(@RequestBody Map<String, Integer> body, HttpServletRequest request) {
        Integer rate = body.get("starsRateKopecks");
        if (rate == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Не указан starsRateKopecks"));
        }
        try {
            adminService.updateStarsRateKopecks(rate);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
        return ResponseEntity.ok(Map.of("starsRateKopecks", adminService.getStarsRateKopecks()));
    }
}
