package ru.sapa.gadalka_backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.domain.Payment;
import ru.sapa.gadalka_backend.domain.PaymentProduct;
import ru.sapa.gadalka_backend.domain.Subscription;
import ru.sapa.gadalka_backend.domain.SubscriptionAutorenewConsentLog;
import ru.sapa.gadalka_backend.domain.SubscriptionPlan;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.type.ConsentAction;
import ru.sapa.gadalka_backend.domain.type.CreditTransactionReason;
import ru.sapa.gadalka_backend.domain.type.PaymentProvider;
import ru.sapa.gadalka_backend.domain.type.PaymentStatus;
import ru.sapa.gadalka_backend.domain.type.PurchaseType;
import ru.sapa.gadalka_backend.exception.PaymentNotFoundException;
import ru.sapa.gadalka_backend.repository.PaymentRepository;
import ru.sapa.gadalka_backend.repository.SubscriptionAutorenewConsentLogRepository;
import ru.sapa.gadalka_backend.repository.SubscriptionPlanRepository;
import ru.sapa.gadalka_backend.service.robokassa.RobokassaClient;
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
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final FortuneCreditService fortuneCreditService;
    private final SubscriptionActivationService subscriptionActivationService;
    private final YooKassaWebhookParser yooKassaWebhookParser;
    private final RobokassaWebhookParser robokassaWebhookParser;
    private final RobokassaClient robokassaClient;
    private final SubscriptionAutorenewConsentLogRepository consentLogRepository;

    /**
     * Registry стратегий: PaymentProvider → реализация.
     * Spring инжектирует все бины, реализующие PaymentProviderStrategy, как список.
     */
    private final Map<PaymentProvider, PaymentProviderStrategy> strategyRegistry;

    public PaymentService(
            PaymentRepository paymentRepository,
            ProductCatalogService productCatalogService,
            SubscriptionPlanRepository subscriptionPlanRepository,
            FortuneCreditService fortuneCreditService,
            SubscriptionActivationService subscriptionActivationService,
            YooKassaWebhookParser yooKassaWebhookParser,
            RobokassaWebhookParser robokassaWebhookParser,
            RobokassaClient robokassaClient,
            SubscriptionAutorenewConsentLogRepository consentLogRepository,
            List<PaymentProviderStrategy> strategies) {

        this.paymentRepository = paymentRepository;
        this.productCatalogService = productCatalogService;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.fortuneCreditService = fortuneCreditService;
        this.subscriptionActivationService = subscriptionActivationService;
        this.yooKassaWebhookParser = yooKassaWebhookParser;
        this.robokassaWebhookParser = robokassaWebhookParser;
        this.robokassaClient = robokassaClient;
        this.consentLogRepository = consentLogRepository;

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
                .creditsToGrant(product.getReadingsCount() + product.getBonusCredits())
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

    /**
     * Инициирует платёж за ПОДПИСКУ. Отличия от пакета знаков:
     * purchaseType = SUBSCRIPTION, creditsToGrant = 0, заполнен subscriptionPlanId —
     * при успешном webhook'е вместо начисления знаков активируется подписка.
     * <p>
     * План оборачивается в транзиентный (не сохраняемый) PaymentProduct,
     * чтобы переиспользовать существующие стратегии провайдеров без изменений:
     * им от продукта нужны только цены и название (для чека/инвойса).
     *
     * @param user              покупатель целиком, а не только его id: при согласии на
     *                          автопродление в журнал согласий пишется ещё и telegram_id —
     *                          единственный идентификатор, который там переживёт удаление
     *                          аккаунта (см. SubscriptionAutorenewConsentLog и миграцию V72).
     * @param autoRenewConsent явное согласие пользователя на автопродление (отдельный
     *                         чекбокс, см. CreateSubscriptionPaymentRequest). Пробрасывается
     *                         в Payment.autoRenewRequested — на его основе RobokassaStrategy
     *                         решает, добавлять ли Recurring=true в форму оплаты.
     */
    @Transactional
    public String createSubscriptionPayment(User user, SubscriptionPlan plan, PaymentProvider provider, boolean autoRenewConsent) {
        Long userId = user.getId();
        PaymentProviderStrategy strategy = getStrategy(provider);
        PaymentProduct planAsProduct = toTransientProduct(plan);

        // Автопродление сейчас реализовано только для Robokassa (chargeRecurring). Для
        // остальных провайдеров согласие игнорируем на бэке — а не полагаемся на то, что
        // фронт не пришлёт его по ошибке: иначе получится подписка с autoRenewEnabled=true,
        // которую нечем будет продлевать (PaymentService#renewSubscription жёстко на Robokassa).
        boolean effectiveAutoRenew = autoRenewConsent && provider == PaymentProvider.ROBOKASSA;

        Payment payment = Payment.builder()
                .userId(userId)
                .productCode("PLAN_" + plan.getId())
                .purchaseType(PurchaseType.SUBSCRIPTION)
                .subscriptionPlanId(plan.getId())
                .provider(provider)
                .status(PaymentStatus.PENDING)
                .amountMinor(strategy.getAmountMinor(planAsProduct))
                .currency(strategy.getCurrency())
                .creditsToGrant(0)
                .autoRenewRequested(effectiveAutoRenew)
                .build();
        payment = paymentRepository.save(payment);

        String url = strategy.initiatePayment(payment, planAsProduct);
        paymentRepository.save(payment);

        // Подписки на момент клика по чекбоксу ещё не существует (появится только
        // после webhook, см. SubscriptionActivationService) — поэтому журналируем
        // согласие по payment_id, subscription_id заполнится отдельно, когда подписка
        // будет создана (см. SubscriptionActivationService#activateFromPayment).
        if (effectiveAutoRenew) {
            consentLogRepository.save(SubscriptionAutorenewConsentLog.builder()
                    .userId(userId)
                    .telegramId(user.getTelegramId())
                    .paymentId(payment.getId())
                    .action(ConsentAction.GRANTED)
                    .build());
        }

        log.info("Подписочный платёж инициирован: internalId={}, provider={}, userId={}, planId={}, autoRenew={}",
                payment.getId(), provider, userId, plan.getId(), effectiveAutoRenew);

        return url;
    }

    /**
     * Инициирует автоматическое рекуррентное списание за продление подписки
     * (вызывается из SubscriptionRenewalScheduler, не из контроллера — пользователь в этом действии не участвует).
     * <p>
     * Создаёт новый Payment для расчётного периода и списывает деньги через
     * {@code RobokassaClient.chargeRecurring}, используя root_payment_id продлеваемой
     * подписки как PreviousInvoiceID. Реальное подтверждение приходит как обычно
     * через ResultURL — {@link #processRobokassaWebhook} и {@link #completePayment}
     * переиспользуются без изменений.
     * <p>
     * Если Robokassa отклонила запрос сразу (невалидная подпись, магазин не подключён к recurring и т.п.) — платёж сразу помечается FAILED, дальше ждать нечего: webhook по такому запросу не придёт.
     *
     * @return true, если Robokassa приняла запрос на списание (это ещё не значит, что деньги списаны — это подтвердит только webhook)
     */
    @Transactional
    public boolean renewSubscription(Subscription subscription) {
        if (subscription.getRootPaymentId() == null) {
            // списывать с PreviousInvoiceID=null нельзя ни в коем случае — только лог и отказ.
            log.error("Автопродление невозможно: у подписки нет rootPaymentId: subscriptionId={}, userId={}", subscription.getId(), subscription.getUserId());
            return false;
        }

        SubscriptionPlan plan = subscriptionPlanRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> new IllegalStateException("План подписки не найден при автопродлении: planId=" + subscription.getPlanId() + ", subscriptionId=" + subscription.getId()));
        // Плана может не быть в публичном каталоге (админ мог его деактивировать),
        // но уже существующим подписчикам продление всё равно должно работать —
        // поэтому ищем по id напрямую, а не через "только активные" методы каталога.
        String itemName = toTransientProduct(plan).getName();

        // Списываем по цене, зафиксированной при оформлении согласия (п. 6.11.3(1) соглашения),
        // а НЕ по текущей цене плана — админ мог поднять или снизить цену уже после того,
        // как пользователь подписался на автопродление. lockedPriceRub может быть null только
        // у подписок, созданных до введения этого поля (V58) — тогда откатываемся на цену
        // плана и громко логируем, чтобы это не осталось незамеченным.
        Integer chargeAmount = subscription.getLockedPriceRub();
        if (chargeAmount == null) {
            chargeAmount = plan.getPriceRub();
            log.warn("У подписки нет lockedPriceRub — списываем по текущей цене плана: subscriptionId={}, planId={}, priceRub={}",
                    subscription.getId(), plan.getId(), chargeAmount);
        }

        Payment payment = Payment.builder()
                .userId(subscription.getUserId())
                .productCode("PLAN_" + plan.getId())
                .purchaseType(PurchaseType.SUBSCRIPTION)
                .subscriptionPlanId(plan.getId())
                .provider(PaymentProvider.ROBOKASSA)
                .status(PaymentStatus.PENDING)
                .amountMinor(chargeAmount)
                .currency("RUB")
                .creditsToGrant(0)
                .autoRenewRequested(true)
                .renewalOfSubscriptionId(subscription.getId())
                .build();
        payment = paymentRepository.save(payment);

        boolean accepted = robokassaClient.chargeRecurring(
                payment.getId(),
                subscription.getRootPaymentId(),
                payment.getAmountMinor(),
                itemName
        );

        if (!accepted) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            log.warn("Автопродление отклонено Robokassa сразу: subscriptionId={}, paymentId={}, userId={}", subscription.getId(), payment.getId(), subscription.getUserId());
        } else {
            log.info("Автопродление инициировано: subscriptionId={}, paymentId={}, userId={}, planId={}", subscription.getId(), payment.getId(), subscription.getUserId(), plan.getId());
        }

        return accepted;
    }

    /**
     * Представление плана подписки в виде продукта для платёжных стратегий.
     * name попадает в чек Robokassa (номенклатура) и в title Stars-инвойса.
     */
    private PaymentProduct toTransientProduct(SubscriptionPlan plan) {
        return PaymentProduct.builder()
                .code("PLAN_" + plan.getId())
                .name("Подписка «" + plan.getName() + "» на " + plan.getDurationDays() + " дней")
                .priceRub(plan.getPriceRub())
                .priceStars(plan.getPriceStars())
                .readingsCount(0)
                .bonusCredits(0)
                .isActive(true)
                .sortOrder(0)
                .build();
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
     * Отменяет платёж по сигналу FailURL — Robokassa редиректит сюда браузер пользователя,
     * когда тот явно отказался от оплаты или она была отклонена (вызывается из PaymentController).
     * <p>
     * Переводит в CANCELLED только если платёж сейчас PENDING и принадлежит ожидаемому провайдеру —
     * это защищает от гонки с ResultURL: если вебхук об успехе уже долетел и платёж SUCCEEDED,
     * редирект FailURL (который мог прийти позже или вообще ошибочно) ничего не затирает.
     * Несуществующий paymentId просто игнорируется — это конечная точка для браузера,
     * 404 тут никому не нужен.
     */
    @Transactional
    public void cancelIfPending(Long paymentId, PaymentProvider expectedProvider) {
        paymentRepository.findById(paymentId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.PENDING && payment.getProvider() == expectedProvider) {
                payment.setStatus(PaymentStatus.CANCELLED);
                paymentRepository.save(payment);
                log.info("Платёж отменён пользователем через FailURL: internalId={}, provider={}", paymentId, expectedProvider);
            }
        });
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
     * Финализирует успешный платёж: SUCCEEDED + начисление знаков ИЛИ активация подписки.
     * Всё в одной транзакции — либо и то и то, либо ничего.
     */
    @Transactional
    protected void completePayment(Payment payment) {
        payment.setStatus(PaymentStatus.SUCCEEDED);
        paymentRepository.save(payment);

        if (payment.getPurchaseType() == PurchaseType.SUBSCRIPTION) {
            subscriptionActivationService.activateFromPayment(payment);
            log.info("Платёж завершён (подписка): internalId={}, userId={}, planId={}",
                    payment.getId(), payment.getUserId(), payment.getSubscriptionPlanId());
            return;
        }

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
