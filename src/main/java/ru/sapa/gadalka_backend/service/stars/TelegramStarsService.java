package ru.sapa.gadalka_backend.service.stars;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.invoices.CreateInvoiceLink;
import org.telegram.telegrambots.meta.api.objects.payments.SuccessfulPayment;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.sapa.gadalka_backend.domain.PaymentProduct;

import java.util.List;

/**
 * Сервис для работы с платежами через Telegram Stars.
 * <p>
 * Telegram Stars — внутренняя валюта Telegram (код XTR).
 * Пользователь покупает Stars в Telegram, затем тратит их в боте/Mini App.
 * <p>
 * Поток:
 * 1. createInvoiceLink() → Telegram API создаёт ссылку на инвойс
 * 2. Фронт вызывает Telegram.WebApp.openInvoice(link)
 * 3. Пользователь платит → бот получает PreCheckoutQuery → отвечает ok
 * 4. Бот получает SuccessfulPayment → handleSuccessfulPayment()
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramStarsService {

    private final TelegramClient telegramClient;

    /**
     * Создаёт ссылку на инвойс Telegram Stars через Bot API.
     *
     * @param internalPaymentId  наш внутренний ID платежа — передаётся как payload,
     *                           возвращается в SuccessfulPayment для связи
     * @param product            продукт из каталога
     * @return invoice URL для передачи в Telegram.WebApp.openInvoice()
     */
    public String createInvoiceLink(Long internalPaymentId, PaymentProduct product) {
        // LabeledPrice — обязательный формат ЮKassa: label + amount в Stars
        var price = new org.telegram.telegrambots.meta.api.objects.payments.LabeledPrice(
                product.getName(),
                product.getPriceStars()
        );

        CreateInvoiceLink invoiceLink = CreateInvoiceLink.builder()
                .title(product.getName())
                .description("Пополнение баланса гаданий в MagicLiora")
                // Payload — наш internal payment id. Вернётся в SuccessfulPayment.
                // Telegram не показывает его пользователю.
                .payload(String.valueOf(internalPaymentId))
                // XTR — официальный ISO-код Telegram Stars
                .currency("XTR")
                .prices(List.of(price))
                .build();

        try {
            String link = telegramClient.execute(invoiceLink);
            log.info("Создана ссылка на Stars-инвойс: internalPaymentId={}, stars={}",
                    internalPaymentId, product.getPriceStars());
            return link;
        } catch (TelegramApiException e) {
            log.error("Ошибка создания Stars-инвойса: internalPaymentId={}, error={}",
                    internalPaymentId, e.getMessage(), e);
            throw new IllegalStateException("Ошибка создания инвойса Telegram Stars", e);
        }
    }

    /**
     * Извлекает наш внутренний ID платежа из SuccessfulPayment update.
     * Payload — строка, которую мы передали при создании инвойса.
     */
    public Long extractInternalPaymentId(SuccessfulPayment successfulPayment) {
        try {
            return Long.parseLong(successfulPayment.getInvoicePayload());
        } catch (NumberFormatException e) {
            log.error("Некорректный payload в SuccessfulPayment: '{}'",
                    successfulPayment.getInvoicePayload());
            throw new IllegalArgumentException("Некорректный invoice payload", e);
        }
    }

    /**
     * Извлекает Telegram charge ID — уникальный идентификатор транзакции на стороне Telegram.
     * Используется как provider_payment_id для идемпотентности.
     */
    public String extractTelegramChargeId(SuccessfulPayment successfulPayment) {
        return successfulPayment.getTelegramPaymentChargeId();
    }
}
