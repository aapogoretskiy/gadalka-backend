package ru.sapa.gadalka_backend.service.robokassa;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.domain.Payment;
import ru.sapa.gadalka_backend.domain.PaymentProduct;
import ru.sapa.gadalka_backend.domain.SubscriptionPlan;
import ru.sapa.gadalka_backend.domain.type.PurchaseType;
import ru.sapa.gadalka_backend.exception.PaymentNotFoundException;
import ru.sapa.gadalka_backend.repository.PaymentRepository;
import ru.sapa.gadalka_backend.repository.SubscriptionPlanRepository;
import ru.sapa.gadalka_backend.service.ProductCatalogService;

/**
 * Сервис генерации промежуточной HTML-страницы для оплаты через Robokassa.
 * <p>
 * Страница содержит автосабмит POST-форму с номенклатурой (Receipt) для фискализации.
 * Пользователь открывает её в браузере — форма мгновенно отправляется на Robokassa.
 * <p>
 * Отдельный сервис нужен чтобы не тянуть Robokassa-специфику ни в PaymentService
 * (он не знает о конкретных провайдерах), ни в PaymentController (тонкий слой API).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RobokassaPageService {

    private final PaymentRepository paymentRepository;
    private final ProductCatalogService productCatalogService;
    private final SubscriptionPlanRepository planRepository;
    private final RobokassaClient robokassaClient;

    /**
     * Загружает платёж и продукт из БД, строит HTML-страницу автосабмит формы.
     *
     * @param paymentId наш внутренний ID платежа
     * @return HTML-строка с POST-формой на Robokassa
     */
    public String buildPaymentPage(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        // Название для номенклатуры чека: пакет знаков берём из каталога,
        // подписку — из плана (в каталоге продуктов её нет)
        String itemName = resolveItemName(payment);

        log.debug("Строим страницу Robokassa: paymentId={}, product={}, amount={}",
                paymentId, payment.getProductCode(), payment.getAmountMinor());

        return robokassaClient.buildPaymentFormHtml(
                payment.getId(),
                payment.getAmountMinor(),
                itemName,
                Boolean.TRUE.equals(payment.getAutoRenewRequested())
        );
    }

    private String resolveItemName(Payment payment) {
        if (payment.getPurchaseType() == PurchaseType.SUBSCRIPTION) {
            SubscriptionPlan plan = planRepository.findById(payment.getSubscriptionPlanId())
                    .orElseThrow(() -> new PaymentNotFoundException(payment.getId()));
            return "Подписка «" + plan.getName() + "» на " + plan.getDurationDays() + " дней";
        }
        PaymentProduct product = productCatalogService.getActiveProduct(payment.getProductCode());
        return product.getName();
    }
}
