package ru.sapa.gadalka_backend.service.yookassa;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.sapa.gadalka_backend.domain.Payment;
import ru.sapa.gadalka_backend.domain.PaymentProduct;
import ru.sapa.gadalka_backend.domain.type.PaymentProvider;
import ru.sapa.gadalka_backend.service.PaymentProviderStrategy;
import ru.sapa.gadalka_backend.service.yookassa.dto.YooKassaPaymentResponse;

/**
 * Стратегия инициирования платежа через ЮKassa.
 * <p>
 * Создаёт платёж в ЮKassa API, сохраняет их ID (providerPaymentId) в объект Payment
 * для последующей идемпотентной обработки webhook.
 * <p>
 * Обработка webhook (подтверждение оплаты) намеренно живёт отдельно —
 * в {@link YooKassaWebhookParser} и {@code PaymentService#processYooKassaWebhook}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YooKassaPaymentStrategy implements PaymentProviderStrategy {

    private final YooKassaClient yooKassaClient;

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.YOOKASSA;
    }

    @Override
    public int getAmountMinor(PaymentProduct product) {
        return product.getPriceRub(); // копейки
    }

    @Override
    public String getCurrency() {
        return "RUB";
    }

    /**
     * Создаёт платёж в ЮKassa и возвращает URL страницы оплаты.
     * <p>
     * Побочный эффект: устанавливает {@code payment.providerPaymentId} —
     * UUID платежа на стороне ЮKassa, нужен для идемпотентной обработки webhook.
     */
    @Override
    public String initiatePayment(Payment payment, PaymentProduct product) {
        YooKassaPaymentResponse response = yooKassaClient.createPayment(
                payment.getId(),
                product.getPriceRub(),
                "Покупка: " + product.getName()
        );

        // Фиксируем ID ЮKassa — вернётся в webhook и позволит найти наш Payment
        payment.setProviderPaymentId(response.getId());

        log.info("ЮKassa платёж инициирован: internalId={}, yookassaId={}",
                payment.getId(), response.getId());

        return response.getConfirmationUrl();
    }
}
