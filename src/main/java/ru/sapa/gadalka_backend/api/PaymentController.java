package ru.sapa.gadalka_backend.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.sapa.gadalka_backend.api.dto.payment.*;
import ru.sapa.gadalka_backend.configuration.AdminProperties;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;
import ru.sapa.gadalka_backend.domain.type.PaymentProvider;
import ru.sapa.gadalka_backend.service.FeatureCostService;
import ru.sapa.gadalka_backend.service.FortuneCreditService;
import ru.sapa.gadalka_backend.service.PaymentService;
import ru.sapa.gadalka_backend.service.PaymentWebhookAckService;
import ru.sapa.gadalka_backend.service.ProductCatalogService;
import ru.sapa.gadalka_backend.service.SubscriptionQuotaService;
import ru.sapa.gadalka_backend.service.robokassa.RobokassaPageService;

import java.net.URI;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController extends BaseController {

    private final ProductCatalogService productCatalogService;
    private final FortuneCreditService fortuneCreditService;
    private final SubscriptionQuotaService subscriptionQuotaService;
    private final FeatureCostService featureCostService;
    private final AdminProperties adminProperties;
    private final PaymentService paymentService;
    private final PaymentWebhookAckService webhookAckService;
    private final RobokassaPageService robokassaPageService;

    /**
     * Активный провайдер рублёвых платежей.
     * Управляется переменной PAYMENT_RUB_PROVIDER. По умолчанию: robokassa.
     */
    @Value("${payment.rub-provider:robokassa}")
    private String rubProvider;

    /**
     * переиспользуем тот же параметр, что и для ЮKassa (YOOKASSA_RETURN_URL)
     */
    @Value("${yookassa.return-url}")
    private String paymentReturnUrl;

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
     * GET /api/v1/payments/config
     * Конфигурация платёжной системы для фронтенда — без авторизации.
     * Позволяет переключать провайдера рублёвых платежей через PAYMENT_RUB_PROVIDER
     * без передеплоя фронтенда.
     */
    @GetMapping("/config")
    public ResponseEntity<PaymentConfigResponse> getConfig() {
        return ResponseEntity.ok(new PaymentConfigResponse(rubProvider));
    }

    /**
     * GET /api/v1/payments/balance
     * Текущий баланс знаков авторизованного пользователя.
     */
    @GetMapping("/balance")
    public ResponseEntity<BalanceResponse> getBalance(HttpServletRequest request) {
        User user = resolveUser(request);
        int balance = fortuneCreditService.getBalance(user.getId());
        boolean hasSubscription = subscriptionQuotaService.findActiveSubscription(user.getId()).isPresent();
        // Фича-гейт закрытого теста подписок: см. SubscriptionController.assertSubscriptionsAvailable
        boolean subscriptionsAvailable = adminProperties.isAdmin(user.getTelegramId());
        return ResponseEntity.ok(new BalanceResponse(balance, hasSubscription, subscriptionsAvailable));
    }

    /**
     * GET /api/v1/payments/spend-options?feature=THREE_CARD
     * Чем пользователь может оплатить конкретную фичу: знаки (стоимость, баланс)
     * и/или квота подписки (остаток). Данные для модалки подтверждения списания.
     */
    @GetMapping("/spend-options")
    public ResponseEntity<SpendOptionsResponse> getSpendOptions(@RequestParam DiaryFeatureType feature,
                                                                HttpServletRequest request) {
        User user = resolveUser(request);
        int cost = featureCostService.getCost(feature);
        int balance = fortuneCreditService.getBalance(user.getId());

        var quotaState = subscriptionQuotaService.getQuotaState(user.getId(), feature);

        return ResponseEntity.ok(new SpendOptionsResponse(
                cost,
                balance,
                balance >= cost,
                quotaState.isPresent(),
                quotaState.map(SubscriptionQuotaService.QuotaState::remaining).orElse(0),
                quotaState.map(SubscriptionQuotaService.QuotaState::total).orElse(0),
                quotaState.map(q -> q.period().name()).orElse(null)
        ));
    }

    /**
     * POST /api/v1/payments/{provider}/create
     * Универсальный эндпоинт создания платежа для любого провайдера.
     * <p>
     * Примеры:
     * <ul>
     *   <li>{@code POST /payments/yookassa/create} — платёж через ЮKassa</li>
     *   <li>{@code POST /payments/stars/create}    — платёж через Telegram Stars</li>
     * </ul>
     * <p>
     * Конвертация "yookassa" / "stars" → {@link PaymentProvider} происходит через
     * {@code PaymentProviderConverter}, зарегистрированный в Spring MVC.
     * <p>
     * Чтобы добавить нового провайдера — не нужно трогать этот файл:
     * достаточно создать {@code PaymentProviderStrategy} + добавить маппинг в конвертер.
     */
    @PostMapping("/{provider}/create")
    public ResponseEntity<CreatePaymentResponse> createPayment(
            @PathVariable PaymentProvider provider,
            @Valid @RequestBody CreatePaymentRequest body,
            HttpServletRequest request) {

        User user = resolveUser(request);
        String url = paymentService.createPayment(user.getId(), body.getProductCode(), provider);
        return ResponseEntity.ok(new CreatePaymentResponse(url));
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
        webhookAckService.acknowledge(PaymentProvider.YOOKASSA, rawPayload);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /api/v1/payments/robokassa/pay/{paymentId}
     * Промежуточная страница для оплаты через Robokassa — без авторизации.
     * <p>
     * Возвращает HTML с автосабмит POST-формой, которая отправляет номенклатуру
     * (Receipt) на Robokassa. Нужна потому что Receipt требует POST,
     * а браузер умеет делать только GET при переходе по ссылке.
     * <p>
     * Пользователь открывает эту страницу → JavaScript сабмитит форму → Robokassa.
     */
    @GetMapping(value = "/robokassa/pay/{paymentId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> robokassaPaymentPage(@PathVariable Long paymentId) {
        log.debug("Запрос страницы оплаты Robokassa: paymentId={}", paymentId);
        String html = robokassaPageService.buildPaymentPage(paymentId);
        return ResponseEntity.ok(html);
    }

    /**
     * POST /api/v1/payments/robokassa/webhook  (ResultURL)
     * Webhook от Robokassa — вызывается после успешной оплаты.
     * <p>
     * Та же двухфазная схема, что и у ЮKassa:
     * 1. Сохраняем параметры в лог-таблицу за ~1мс → отвечаем Robokassa.
     * 2. PaymentWebhookAckService обрабатывает асинхронно по расписанию.
     * <p>
     * КРИТИЧНО: Robokassa ждёт plain text "OK{InvId}" (например, "OK42").
     * InvId читается из параметров до сохранения — это мгновенно,
     * поэтому двухфазность не мешает быстро ответить нужным текстом.
     * <p>
     * Параметры приходят как form-параметры (application/x-www-form-urlencoded).
     * IP-адреса Robokassa фильтруются на уровне {@code RobokassaWebhookIpFilter}.
     */
    @PostMapping(value = "/robokassa/webhook", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> robokassaWebhook(
            @RequestParam("OutSum") String outSum,
            @RequestParam("InvId") String invId,
            @RequestParam("SignatureValue") String signatureValue) {

        log.debug("Получен webhook от Robokassa: InvId={}, OutSum={}", invId, outSum);

        // Фаза 1: сохраняем параметры в лог и отвечаем немедленно
        webhookAckService.acknowledgeRobokassa(outSum, invId, signatureValue);

        // Robokassa требует именно этот формат — иначе будет слать повторно
        return ResponseEntity.ok("OK" + invId);
    }

    /**
     * GET /api/v1/payments/robokassa/fail  (FailURL)
     * Robokassa редиректит сюда браузер пользователя, если он явно отказался от оплаты
     * или она была отклонена. Без авторизации — это обычная браузерная навигация,
     * а не запрос из приложения.
     * <p>
     * Подпись здесь не проверяем: операция только гасит зависшую PENDING-запись,
     * кредитов не начисляет — максимум, что может сделать поддельный запрос,
     * это отменить чужую незавершённую попытку покупки (не критично, можно купить заново).
     * <p>
     * После отмены отправляем пользователя обратно в бота — та же логика, что и у ЮKassa.
     */
    @GetMapping("/robokassa/fail")
    public ResponseEntity<Void> robokassaFail(@RequestParam(value = "InvId", required = false) String invId) {
        // InvId необязателен намеренно: что бы ни случилось с параметрами редиректа,
        // пользователь должен мягко вернуться в бота, а не увидеть 500/400 в браузере —
        // это конечная точка для чужого редиректа, который мы не полностью контролируем.
        if (invId == null || invId.isBlank()) {
            log.warn("Robokassa FailURL: запрос без InvId");
        } else {
            try {
                paymentService.cancelIfPending(Long.parseLong(invId), PaymentProvider.ROBOKASSA);
            } catch (NumberFormatException e) {
                log.warn("Robokassa FailURL: некорректный InvId={}", invId);
            }
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(paymentReturnUrl)).build();
    }
}
