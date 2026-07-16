package ru.sapa.gadalka_backend.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.sapa.gadalka_backend.api.dto.payment.CreatePaymentResponse;
import ru.sapa.gadalka_backend.api.dto.subscription.CreateSubscriptionPaymentRequest;
import ru.sapa.gadalka_backend.api.dto.subscription.MySubscriptionResponse;
import ru.sapa.gadalka_backend.api.dto.subscription.SubscriptionPlanDto;
import ru.sapa.gadalka_backend.configuration.AdminProperties;
import ru.sapa.gadalka_backend.domain.SubscriptionPlan;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.type.PaymentProvider;
import ru.sapa.gadalka_backend.service.PaymentService;
import ru.sapa.gadalka_backend.service.SubscriptionCancellationService;
import ru.sapa.gadalka_backend.service.SubscriptionCatalogService;
import ru.sapa.gadalka_backend.service.SubscriptionQuotaService;

import java.util.List;

/**
 * Публичное API подписок: каталог планов, моя подписка, покупка.
 * <p>
 * ФИЧА-ГЕЙТ: пока подписки в закрытом тесте, покупка доступна только
 * администраторам (whitelist ADMIN_TELEGRAM_IDS). Каталог отдаётся всем —
 * фронт показывает вкладку задизейбленной («Будет доступна позже»).
 * Когда откроемся для всех — убрать проверку в {@link #assertSubscriptionsAvailable}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Подписки", description = "Каталог планов и покупка подписки")
public class SubscriptionController extends BaseController {

    private final SubscriptionCatalogService subscriptionCatalogService;
    private final SubscriptionQuotaService subscriptionQuotaService;
    private final SubscriptionCancellationService subscriptionCancellationService;
    private final PaymentService paymentService;
    private final AdminProperties adminProperties;

    /**
     * GET /api/v1/subscriptions/plans
     * Каталог активных планов — без авторизации (как /payments/products).
     */
    @GetMapping("/plans")
    @Operation(summary = "Каталог планов подписки")
    public ResponseEntity<List<SubscriptionPlanDto>> getPlans() {
        return ResponseEntity.ok(subscriptionCatalogService.getActivePlans());
    }

    /**
     * GET /api/v1/subscriptions/my
     * Активная подписка пользователя с остатками квот. 404 — подписки нет.
     */
    @GetMapping("/my")
    @Operation(summary = "Моя активная подписка с остатками квот")
    public ResponseEntity<MySubscriptionResponse> getMySubscription(HttpServletRequest request) {
        User user = resolveUser(request);
        return subscriptionCatalogService.getMySubscription(user.getId())
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Активной подписки нет"));
    }

    /**
     * POST /api/v1/subscriptions/{provider}/create
     * Создаёт платёж за подписку (аналог /payments/{provider}/create для пакетов знаков).
     * <p>
     * Проверки: фича-гейт (только админы, пока тестируем), активный план,
     * отсутствие другой активной подписки (бизнес-правило «одна подписка»).
     */
    @PostMapping("/{provider}/create")
    @Operation(summary = "Купить подписку через выбранного провайдера")
    public ResponseEntity<CreatePaymentResponse> createSubscriptionPayment(
            @PathVariable PaymentProvider provider,
            @RequestBody CreateSubscriptionPaymentRequest body,
            HttpServletRequest request) {

        User user = resolveUser(request);
        assertSubscriptionsAvailable(user);

        SubscriptionPlan plan = subscriptionCatalogService
                .getActivePlan(body.planId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "План подписки не найден"));

        if (subscriptionQuotaService.findActiveSubscription(user.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "У вас уже есть активная подписка. Новую можно оформить после её окончания.");
        }

        String url = paymentService.createSubscriptionPayment(user.getId(), plan, provider);
        return ResponseEntity.ok(new CreatePaymentResponse(url));
    }

    /**
     * POST /api/v1/subscriptions/cancel
     * Отказ от активной подписки: слот освобождается, квоты и срок сгорают.
     * Деньги автоматически НЕ возвращаются — фронт предупреждает об этом
     * в подтверждении, возврат оформляется через поддержку (ст. 32 ЗоЗПП).
     */
    @PostMapping("/cancel")
    @Operation(summary = "Отказаться от активной подписки (без автовозврата)")
    public ResponseEntity<?> cancelSubscription(HttpServletRequest request) {
        User user = resolveUser(request);
        try {
            subscriptionCancellationService.cancelByUser(user.getId());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        return ResponseEntity.ok().build();
    }

    /**
     * Фича-гейт закрытого теста: покупка подписок доступна только админам.
     * Дублирует ограничение UI на бэке — прямой вызов API мимо интерфейса
     * не должен позволять купить подписку до открытия фичи.
     */
    private void assertSubscriptionsAvailable(User user) {
        if (!adminProperties.isAdmin(user.getTelegramId())) {
            log.info("Попытка купить подписку до открытия фичи: userId={}", user.getId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Подписки будут доступны позже");
        }
    }
}
