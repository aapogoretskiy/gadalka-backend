package ru.sapa.gadalka_backend.service.robokassa;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.sapa.gadalka_backend.domain.Payment;
import ru.sapa.gadalka_backend.domain.PaymentProduct;
import ru.sapa.gadalka_backend.domain.type.PaymentProvider;
import ru.sapa.gadalka_backend.service.PaymentProviderStrategy;

/**
 * Стратегия инициирования платежа через Robokassa.
 * <p>
 * Принципиальное отличие от ЮKassa: здесь нет HTTP-вызова к внешнему API.
 * Вместо этого формируется подписанный URL, по которому пользователь переходит
 * напрямую на страницу оплаты Robokassa.
 * <p>
 * Поток:
 * 1. {@code initiatePayment} → строит URL с подписью (Пароль #1)
 * 2. Фронт открывает URL через {@code Telegram.WebApp.openLink()}
 * 3. Пользователь оплачивает → Robokassa вызывает ResultURL (webhook)
 * 4. Webhook обрабатывается в {@code PaymentController#robokassaWebhook}
 * <p>
 * {@code providerPaymentId} у Robokassa нет на этапе создания — он не нужен,
 * потому что связь с нашим Payment происходит через InvId = payment.getId().
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RobokassaPaymentStrategy implements PaymentProviderStrategy {

    private final RobokassaClient robokassaClient;

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.ROBOKASSA;
    }

    @Override
    public int getAmountMinor(PaymentProduct product) {
        return product.getPriceRub(); // копейки, как у ЮKassa
    }

    @Override
    public String getCurrency() {
        return "RUB";
    }

    /**
     * Формирует подписанный URL страницы оплаты Robokassa.
     * <p>
     * В отличие от ЮKassa, {@code providerPaymentId} НЕ устанавливается —
     * Robokassa возвращает наш InvId (= payment.getId()) в webhook,
     * что достаточно для идентификации платежа.
     */
    @Override
    public String initiatePayment(Payment payment, PaymentProduct product) {
        String url = robokassaClient.buildPaymentUrl(
                payment.getId(),
                product.getPriceRub(),
                "Покупка: " + product.getName()
        );

        log.info("Robokassa URL сформирован: internalId={}, product={}",
                payment.getId(), product.getCode());

        return url;
    }
}
