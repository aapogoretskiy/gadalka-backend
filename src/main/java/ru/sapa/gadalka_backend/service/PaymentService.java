package ru.sapa.gadalka_backend.service;

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
import ru.sapa.gadalka_backend.service.robokassa.RobokassaWebhookParser;
import ru.sapa.gadalka_backend.service.yookassa.YooKassaWebhookParser;
import ru.sapa.gadalka_backend.service.yookassa.dto.YooKassaWebhookPayload;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Оркестратор платёжного процесса.
 * <p>
 * Координирует: создание Payment, вызов провайдера через стратегию, начисление кредитов.
 * <p>
 * Чтобы добавить нового провайдера — достаточно реализовать {@link PaymentProviderStrategy}
 * и пометить класс {@code @Component}. Этот сервис не нужно трогать.
 */
@Slf4j
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ProductCatalogService productCatalogService;
    private final FortuneCreditService fortuneCreditService;
    private final YooKassaWebhookParser yooKassaWebhookParser;
    private final RobokassaWebhookParser robokassaWebhookParser;

    /**
     * Registry стратегий: PaymentProvider → реализация.
     * Spring инжектирует все бины, реализующие PaymentProviderStrategy, как список.
     */
    private final Map<PaymentProvider, PaymentProviderStrategy> strategyRegistry;

    public PaymentService(
            PaymentRepository paymentRepository,
            ProductCatalogService productCatalogService,
            FortuneCreditService fortuneCreditService,
            YooKassaWebhookParser yooKassaWebhookParser,
            RobokassaWebhookParser robokassaWebhookParser,
            List<PaymentProviderStrategy> strategies) {

        this.paymentRepository = paymentRepository;
        this.productCatalogService = productCatalogService;
        this.fortuneCreditService = fortuneCreditService;
        this.yooKassaWebhookParser = yooKassaWebhookParser;
        this.robokassaWebhookParser = robokassaWebhookParser;

        // Собираем registry: каждая стратегия сама заявляет свой provider()
        this.strategyRegistry = strategies.stream()
                .collect(Collectors.toMap(PaymentProviderStrategy::provider, s -> s));

        log.info("Платёжные стратегии загружены: {}", strategyRegistry.keySet());
    }

    // ──────────────────────────────────────────────
    // Создание платежей (единая точка входа)
    // ──────────────────────────────────────────────

    /**
     * Инициирует платёж через указанного провайдера.
     * Возвращает URL, который нужно передать пользователю
     * (страница оплаты ЮKassa, invoice link Telegram Stars, ...).
     */
    @Transactional
    public String createPayment(Long userId, String productCode, PaymentProvider provider) {
        PaymentProviderStrategy strategy = getStrategy(provider);
        PaymentProduct product = productCatalogService.getActiveProduct(productCode);

        // Создаём Payment(PENDING) — нужен ID для передачи провайдеру
        Payment payment = Payment.builder()
                .userId(userId)
                .productCode(product.getCode())
                .provider(provider)
                .status(PaymentStatus.PENDING)
                .amountMinor(strategy.getAmountMinor(product))
                .currency(strategy.getCurrency())
                .creditsToGrant(product.getReadingsCount())
                .build();
        payment = paymentRepository.save(payment);

        // Вызываем провайдера — он может мутировать payment (например, установить providerPaymentId)
        String url = strategy.initiatePayment(payment, product);

        // Сохраняем после вызова — фиксируем любые изменения от провайдера
        paymentRepository.save(payment);

        log.info("Платёж инициирован: internalId={}, provider={}, userId={}, product={}",
                payment.getId(), provider, userId, productCode);

        return url;
    }

    // ──────────────────────────────────────────────
    // Обработка результатов (провайдер-специфично)
    // ──────────────────────────────────────────────

    /**
     * Обрабатывает webhook от ЮKassa (вызывается из PaymentWebhookAckService).
     * Парсит payload → обновляет статус Payment → начисляет кредиты при успехе.
     */
    public void processYooKassaWebhook(String rawPayload) {
        YooKassaWebhookPayload payload = yooKassaWebhookParser.parse(rawPayload);

        if (yooKassaWebhookParser.isPaymentSucceeded(payload)) {
            handleYooKassaSuccess(payload);
        } else if (yooKassaWebhookParser.isPaymentCancelled(payload)) {
            handleYooKassaCancellation(payload);
        } else {
            log.debug("Пропускаем webhook event: {}", payload.getEvent());
        }
    }

    /**
     * Обрабатывает webhook от Robokassa (ResultURL).
     * Проверяет подпись через Пароль#2, начисляет кредиты при успехе.
     *
     * @param outSum         сумма платежа (строка от Робокассы)
     * @param invId          наш internal payment ID
     * @param signatureValue подпись для верификации
     * @throws SecurityException если подпись невалидна
     */
    @Transactional
    public void processRobokassaWebhook(String outSum, String invId, String signatureValue) {
        if (!robokassaWebhookParser.isSignatureValid(outSum, invId, signatureValue)) {
            throw new SecurityException("Невалидная подпись Robokassa webhook: invId=" + invId);
        }

        Long internalPaymentId = robokassaWebhookParser.extractInvId(invId);

        // Идемпотентность: если уже обработали — просто логируем и выходим
        Payment payment = paymentRepository.findById(internalPaymentId)
                .orElseThrow(() -> new PaymentNotFoundException(internalPaymentId));

        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            log.warn("Robokassa webhook: платёж уже обработан: internalId={}", internalPaymentId);
            return;
        }

        completePayment(payment);
    }

    /**
     * Обрабатывает успешный Stars-платёж (вызывается из GadalkaTelegramBot).
     * providerPaymentId = telegramPaymentChargeId (уникален, используется для идемпотентности).
     */
    @Transactional
    public void processStarsSuccess(Long internalPaymentId, String telegramChargeId) {
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

    private PaymentProviderStrategy getStrategy(PaymentProvider provider) {
        PaymentProviderStrategy strategy = strategyRegistry.get(provider);
        if (strategy == null) {
            throw new IllegalStateException(
                    "Нет зарегистрированной стратегии для провайдера: " + provider);
        }
        return strategy;
    }

    @Transactional
    protected void handleYooKassaSuccess(YooKassaWebhookPayload payload) {
        String yookassaPaymentId = payload.getYookassaPaymentId();

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
