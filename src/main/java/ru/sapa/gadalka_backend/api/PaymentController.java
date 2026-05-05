package ru.sapa.gadalka_backend.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.sapa.gadalka_backend.api.dto.payment.*;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.type.PaymentProvider;
import ru.sapa.gadalka_backend.service.FortuneCreditService;
import ru.sapa.gadalka_backend.service.PaymentService;
import ru.sapa.gadalka_backend.service.PaymentWebhookAckService;
import ru.sapa.gadalka_backend.service.ProductCatalogService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController extends BaseController {

    private final ProductCatalogService productCatalogService;
    private final FortuneCreditService fortuneCreditService;
    private final PaymentService paymentService;
    private final PaymentWebhookAckService webhookAckService;

    /**
     * GET /api/v1/payments/products
     * Каталог продуктов — без авторизации, для отображения на экране покупки.
     */
    @GetMapping("/products")
    public ResponseEntity<List<PaymentProductDto>> getProducts() {
        List<PaymentProductDto> products = productCatalogService.getActiveProducts()
                .stream()
                .map(PaymentProductDto::from)
                .toList();
        return ResponseEntity.ok(products);
    }

    /**
     * GET /api/v1/payments/balance
     * Текущий баланс гаданий авторизованного пользователя.
     */
    @GetMapping("/balance")
    public ResponseEntity<BalanceResponse> getBalance(HttpServletRequest request) {
        User user = resolveUser(request);
        int balance = fortuneCreditService.getBalance(user.getId());
        boolean hasSubscription = fortuneCreditService.canUseFeature(user.getId()) && balance == 0;
        return ResponseEntity.ok(new BalanceResponse(balance, hasSubscription));
    }

    /**
     * POST /api/v1/payments/yookassa/create
     * Создаёт платёж через ЮKassa. Возвращает URL страницы оплаты.
     * Фронт редиректит пользователя на этот URL.
     */
    @PostMapping("/yookassa/create")
    public ResponseEntity<CreatePaymentResponse> createYooKassaPayment(
            @Valid @RequestBody CreatePaymentRequest body,
            HttpServletRequest request) {

        User user = resolveUser(request);
        String confirmationUrl = paymentService.createYooKassaPayment(user.getId(), body.getProductCode());
        return ResponseEntity.ok(new CreatePaymentResponse(confirmationUrl));
    }

    /**
     * POST /api/v1/payments/stars/create
     * Создаёт инвойс Telegram Stars. Возвращает invoice URL.
     * Фронт передаёт его в Telegram.WebApp.openInvoice(url, callback).
     */
    @PostMapping("/stars/create")
    public ResponseEntity<CreatePaymentResponse> createStarsPayment(
            @Valid @RequestBody CreatePaymentRequest body,
            HttpServletRequest request) {

        User user = resolveUser(request);
        String invoiceUrl = paymentService.createStarsPayment(user.getId(), body.getProductCode());
        return ResponseEntity.ok(new CreatePaymentResponse(invoiceUrl));
    }

    /**
     * POST /api/v1/payments/yookassa/webhook
     * Webhook от ЮKassa — без авторизации (ЮKassa не передаёт токены).
     * <p>
     * ВАЖНО: этот эндпоинт должен ответить HTTP 200 как можно быстрее.
     * Реальная обработка происходит асинхронно в PaymentWebhookAckService.
     */
    @PostMapping("/yookassa/webhook")
    public ResponseEntity<Void> yookassaWebhook(@RequestBody String rawPayload) {
        log.debug("Получен webhook от ЮKassa, длина payload: {} байт", rawPayload.length());
        // Сохраняем сырой payload за ~1мс — обработка асинхронная
        webhookAckService.acknowledge(PaymentProvider.YOOKASSA, rawPayload);
        return ResponseEntity.ok().build();
    }
}
