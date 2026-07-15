package ru.sapa.gadalka_backend.service.stars;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.sapa.gadalka_backend.domain.Payment;
import ru.sapa.gadalka_backend.domain.PaymentProduct;
import ru.sapa.gadalka_backend.domain.type.PaymentProvider;
import ru.sapa.gadalka_backend.domain.type.PurchaseType;
import ru.sapa.gadalka_backend.service.PaymentProviderStrategy;

/**
 * Стратегия инициирования платежа через Telegram Stars.
 * <p>
 * Создаёт invoice link через Telegram Bot API и возвращает его фронту.
 * Фронт передаёт ссылку в {@code Telegram.WebApp.openInvoice(link, callback)}.
 * <p>
 * Подтверждение оплаты приходит не через HTTP webhook, а через Telegram Bot update
 * ({@code SuccessfulPayment}) — обрабатывается в {@code GadalkaTelegramBot}.
 * {@code providerPaymentId} (telegramChargeId) устанавливается там же, при подтверждении.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramStarsPaymentStrategy implements PaymentProviderStrategy {

    private final TelegramStarsService starsService;

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.TELEGRAM_STARS;
    }

    @Override
    public int getAmountMinor(PaymentProduct product) {
        return product.getPriceStars(); // Stars (целые числа, минимальная единица = 1 Star)
    }

    @Override
    public String getCurrency() {
        return "XTR";
    }

    /**
     * Создаёт invoice link и возвращает его фронту.
     * <p>
     * В отличие от ЮKassa, {@code providerPaymentId} здесь НЕ устанавливается —
     * он появляется только после подтверждения оплаты пользователем (в SuccessfulPayment update).
     */
    @Override
    public String initiatePayment(Payment payment, PaymentProduct product) {
        // Для подписки — своё описание в окне оплаты (product здесь — транзиентное представление плана)
        String description = payment.getPurchaseType() == PurchaseType.SUBSCRIPTION
                ? "Оформление подписки в MagicLiora"
                : "Пополнение баланса знаков в MagicLiora";
        String invoiceLink = starsService.createInvoiceLink(payment.getId(), product, description);

        log.info("Stars invoice создан: internalId={}, stars={}",
                payment.getId(), product.getPriceStars());

        return invoiceLink;
    }
}
