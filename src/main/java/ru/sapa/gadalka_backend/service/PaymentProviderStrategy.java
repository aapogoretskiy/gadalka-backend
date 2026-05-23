package ru.sapa.gadalka_backend.service;

import ru.sapa.gadalka_backend.domain.Payment;
import ru.sapa.gadalka_backend.domain.PaymentProduct;
import ru.sapa.gadalka_backend.domain.type.PaymentProvider;

/**
 * Стратегия для конкретного платёжного провайдера.
 * <p>
 * Чтобы добавить нового провайдера:
 * <ol>
 *   <li>Добавить значение в {@link PaymentProvider}</li>
 *   <li>Добавить маппинг в {@code PaymentProviderConverter}</li>
 *   <li>Создать класс, реализующий этот интерфейс, и пометить его {@code @Component}</li>
 * </ol>
 * <p>
 * {@link PaymentService} автоматически подхватит новую реализацию через Spring-инъекцию
 * списка стратегий.
 * <p>
 * Обработка обратных вызовов (webhook, Telegram Bot update) намеренно вынесена за пределы
 * этого интерфейса — у каждого провайдера свой транспорт для подтверждения оплаты.
 */
public interface PaymentProviderStrategy {

    /**
     * Провайдер, который реализует данная стратегия.
     * Используется как ключ в registry {@link PaymentService}.
     */
    PaymentProvider provider();

    /**
     * Сумма платежа в минимальных единицах валюты (копейки для RUB, Stars для XTR и т.д.).
     * Сохраняется в {@link Payment} до вызова провайдера.
     */
    int getAmountMinor(PaymentProduct product);

    /**
     * ISO-код валюты ("RUB", "XTR", ...).
     * Сохраняется в {@link Payment} до вызова провайдера.
     */
    String getCurrency();

    /**
     * Инициирует платёж у провайдера и возвращает URL для пользователя
     * (страница оплаты ЮKassa, invoice link Telegram Stars, и т.д.).
     * <p>
     * Метод может мутировать {@code payment} — например, установить
     * {@code providerPaymentId} если провайдер возвращает его синхронно.
     * {@link PaymentService} сохранит объект после вызова.
     *
     * @param payment объект Payment с уже присвоенным ID из БД
     * @param product продукт из каталога
     * @return URL, который нужно передать пользователю
     */
    String initiatePayment(Payment payment, PaymentProduct product);
}
