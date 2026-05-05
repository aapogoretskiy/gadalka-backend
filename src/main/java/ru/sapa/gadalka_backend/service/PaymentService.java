package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.domain.Payment;
import ru.sapa.gadalka_backend.domain.PaymentProduct;
import ru.sapa.gadalka_backend.domain.type.CreditTransactionReason;
import ru.sapa.gadalka_backend.domain.type.PaymentProvider;
import ru.sapa.gadalka_backend.domain.type.PaymentStatus;
import ru.sapa.gadalka_backend.exception.PaymentNotFoundException;
import ru.sapa.gadalka_backend.repository.PaymentRepository;
import ru.sapa.gadalka_backend.service.stars.TelegramStarsService;
import ru.sapa.gadalka_backend.service.yookassa.YooKassaClient;
import ru.sapa.gadalka_backend.service.yookassa.YooKassaWebhookParser;
import ru.sapa.gadalka_backend.service.yookassa.dto.YooKassaPaymentResponse;
import ru.sapa.gadalka_backend.service.yookassa.dto.YooKassaWebhookPayload;

/**
 * Оркестратор платёжного процесса.
 * Координирует: создание Payment, вызов провайдеров, начисление кредитов.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ProductCatalogService productCatalogService;
    private final FortuneCreditService fortuneCreditService;
    private final YooKassaClient yooKassaClient;
    private final YooKassaWebhookParser webhookParser;
    private final TelegramStarsService starsService;

    // ──────────────────────────────────────────────
    // Создание платежей
    // ──────────────────────────────────────────────

    /**
     * Инициирует платёж через ЮKassa.
     * Создаёт Payment(PENDING) → вызывает ЮKassa API → возвращает URL для редиректа.
     *
     * @return URL страницы оплаты ЮKassa
     */
    @Transactional
    public String createYooKassaPayment(Long userId, String productCode) {
        PaymentProduct product = productCatalogService.getActiveProduct(productCode);

        // Сначала сохраняем платёж в БД — нам нужен его ID как idempotency key для ЮKassa
        Payment payment = createPendingPayment(userId, product, PaymentProvider.YOOKASSA,
                product.getPriceRub(), "RUB");

        // Вызываем ЮKassa API
        YooKassaPaymentResponse response = yooKassaClient.createPayment(
                payment.getId(),
                product.getPriceRub(),
                "Покупка: " + product.getName()
        );

        // Сохраняем ID платежа от ЮKassa — он нужен для идемпотентности webhook
        payment.setProviderPaymentId(response.getId());
        paymentRepository.save(payment);

        log.info("Платёж ЮKassa создан: internalId={}, yookassaId={}, userId={}, product={}",
                payment.getId(), response.getId(), userId, productCode);

        return response.getConfirmationUrl();
    }

    /**
     * Инициирует платёж через Telegram Stars.
     * Создаёт Payment(PENDING) → создаёт invoice link → возвращает его фронту.
     *
     * @return invoice URL для Telegram.WebApp.openInvoice()
     */
    @Transactional
    public String createStarsPayment(Long userId, String productCode) {
        PaymentProduct product = productCatalogService.getActiveProduct(productCode);

        Payment payment = createPendingPayment(userId, product, PaymentProvider.TELEGRAM_STARS,
                product.getPriceStars(), "XTR");

        String invoiceLink = starsService.createInvoiceLink(payment.getId(), product);

        log.info("Stars-инвойс создан: internalId={}, userId={}, product={}, stars={}",
                payment.getId(), userId, productCode, product.getPriceStars());

        return invoiceLink;
    }

    // ──────────────────────────────────────────────
    // Обработка результатов
    // ──────────────────────────────────────────────

    /**
     * Обрабатывает webhook от ЮKassa (вызывается из PaymentWebhookAckService).
     * Парсит payload → обновляет статус Payment → начисляет кредиты при успехе.
     */
    public void processYooKassaWebhook(String rawPayload) {
        YooKassaWebhookPayload payload = webhookParser.parse(rawPayload);

        if (webhookParser.isPaymentSucceeded(payload)) {
            handleYooKassaSuccess(payload);
        } else if (webhookParser.isPaymentCancelled(payload)) {
            handleYooKassaCancellation(payload);
        } else {
            log.debug("Пропускаем webhook event: {}", payload.getEvent());
        }
    }

    /**
     * Обрабатывает успешный Stars-платёж (вызывается из GadalkaTelegramBot).
     * providerPaymentId = telegramPaymentChargeId (уникален, используется для идемпотентности).
     */
    @Transactional
    public void processStarsSuccess(Long internalPaymentId, String telegramChargeId) {
        // Идемпотентность: если уже обработали — просто логируем и выходим
        if (paymentRepository.existsByProviderPaymentIdAndStatus(telegramChargeId, PaymentStatus.SUCCEEDED)) {
            log.warn("Stars платёж уже обработан: chargeId={}", telegramChargeId);
            return;
        }

        Payment payment = paymentRepository.findById(internalPaymentId)
                .orElseThrow(() -> new PaymentNotFoundException(internalPaymentId));

        payment.setProviderPaymentId(telegramChargeId);
        completePayment(payment);
    }

    // ──────────────────────────────────────────────
    // Вспомогательные методы
    // ──────────────────────────────────────────────

    private Payment createPendingPayment(Long userId, PaymentProduct product,
                                          PaymentProvider provider, int amountMinor, String currency) {
        Payment payment = Payment.builder()
                .userId(userId)
                .productCode(product.getCode())
                .provider(provider)
                .status(PaymentStatus.PENDING)
                .amountMinor(amountMinor)
                .currency(currency)
                // Фиксируем количество гаданий на момент создания платежа
                .creditsToGrant(product.getReadingsCount())
                .build();
        return paymentRepository.save(payment);
    }

    @Transactional
    protected void handleYooKassaSuccess(YooKassaWebhookPayload payload) {
        String yookassaPaymentId = payload.getYookassaPaymentId();

        // Идемпотентность: уже обработали — выходим
        if (paymentRepository.existsByProviderPaymentIdAndStatus(yookassaPaymentId, PaymentStatus.SUCCEEDED)) {
            log.warn("ЮKassa платёж уже обработан: yookassaId={}", yookassaPaymentId);
            return;
        }

        Payment payment = paymentRepository.findByProviderPaymentId(yookassaPaymentId)
                .orElseThrow(() -> new PaymentNotFoundException(yookassaPaymentId));

        completePayment(payment);
    }

    @Transactional
    protected void handleYooKassaCancellation(YooKassaWebhookPayload payload) {
        String yookassaPaymentId = payload.getYookassaPaymentId();

        paymentRepository.findByProviderPaymentId(yookassaPaymentId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.setStatus(PaymentStatus.CANCELLED);
                paymentRepository.save(payment);
                log.info("Платёж отменён: yookassaId={}, internalId={}", yookassaPaymentId, payment.getId());
            }
        });
    }

    /**
     * Финализирует успешный платёж: SUCCEEDED + начисление гаданий.
     * Всё в одной транзакции — либо и то и то, либо ничего.
     */
    @Transactional
    protected void completePayment(Payment payment) {
        payment.setStatus(PaymentStatus.SUCCEEDED);
        paymentRepository.save(payment);

        fortuneCreditService.grantCredits(
                payment.getUserId(),
                payment.getCreditsToGrant(),
                CreditTransactionReason.PAYMENT,
                payment.getId()
        );

        log.info("Платёж завершён: internalId={}, userId={}, grantedCredits={}",
                payment.getId(), payment.getUserId(), payment.getCreditsToGrant());
    }
}
