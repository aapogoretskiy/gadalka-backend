package ru.sapa.gadalka_backend.api.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CreatePaymentResponse {

    /**
     * Для ЮKassa: URL страницы оплаты (редирект).
     * Для Stars: invoice URL для Telegram.WebApp.openInvoice().
     */
    private String paymentUrl;
}
