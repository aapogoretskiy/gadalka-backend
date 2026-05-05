package ru.sapa.gadalka_backend.service.yookassa.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/**
 * Тело запроса на создание платежа в ЮKassa API.
 * Документация: https://yookassa.ru/developers/api#create_payment
 */
@Getter
@Builder
public class YooKassaCreatePaymentRequest {

    @JsonProperty("amount")
    private YooKassaAmount amount;

    @JsonProperty("confirmation")
    private Confirmation confirmation;

    @JsonProperty("description")
    private String description;

    /**
     * Наш внутренний идентификатор платежа.
     * Хранится в ЮKassa и возвращается в webhook — так мы связываем их платёж с нашим.
     */
    @JsonProperty("metadata")
    private Metadata metadata;

    @JsonProperty("capture")
    private boolean capture;

    @Getter
    @Builder
    public static class Confirmation {
        @JsonProperty("type")
        private String type;       // "redirect"

        @JsonProperty("return_url")
        private String returnUrl;  // URL после оплаты
    }

    @Getter
    @Builder
    public static class Metadata {
        @JsonProperty("internal_payment_id")
        private String internalPaymentId;  // наш Payment.id
    }
}
