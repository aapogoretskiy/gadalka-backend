package ru.sapa.gadalka_backend.service.robokassa;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.sapa.gadalka_backend.domain.Payment;
import ru.sapa.gadalka_backend.domain.PaymentProduct;
import ru.sapa.gadalka_backend.domain.type.PaymentProvider;
import ru.sapa.gadalka_backend.service.PaymentProviderStrategy;

/**
 * Стратегия инициирования платежа через Robokassa.
 * <p>
 * Поток:
 * 1. {@code initiatePayment} → возвращает URL нашей промежуточной страницы
 * 2. Фронт открывает его через {@code Telegram.WebApp.openLink()}
 * 3. Браузер загружает страницу → JavaScript сабмитит POST-форму на Robokassa
 * 4. Пользователь оплачивает → Robokassa вызывает ResultURL (webhook)
 * 5. Webhook обрабатывается в {@code PaymentController#robokassaWebhook}
 * <p>
 * Промежуточная страница нужна потому что Receipt (номенклатура для фискализации)
 * требует POST — браузер не может сделать POST при переходе по ссылке.
 * <p>
 * {@code providerPaymentId} у Robokassa нет на этапе создания — не нужен,
 * потому что связь с нашим Payment происходит через InvId = payment.getId().
 */
@Slf4j
@Component
public class RobokassaPaymentStrategy implements PaymentProviderStrategy {

    /** Базовый URL приложения — используется для построения URL промежуточной страницы. */
    @Value("${telegram.bot.app-url}")
    private String appUrl;

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.ROBOKASSA;
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
     * Возвращает URL нашей промежуточной страницы, которая делает POST на Robokassa.
     * Фронт открывает этот URL через WebApp.openLink() — пользователь попадает на оплату.
     */
    @Override
    public String initiatePayment(Payment payment, PaymentProduct product) {
        // Убираем trailing slash у appUrl, чтобы не получить двойной слэш
        String base = appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
        String pageUrl = base + "/api/v1/payments/robokassa/pay/" + payment.getId();

        log.info("Robokassa: промежуточная страница сформирована: internalId={}, product={}, url={}",
                payment.getId(), product.getCode(), pageUrl);

        return pageUrl;
    }
}
