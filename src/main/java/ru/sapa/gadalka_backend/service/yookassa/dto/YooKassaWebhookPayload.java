package ru.sapa.gadalka_backend.service.yookassa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Payload webhook-уведомления от ЮKassa.
 * Документация: https://yookassa.ru/developers/using-api/webhooks
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class YooKassaWebhookPayload {

    /** Тип события: payment.succeeded, payment.canceled и т.д. */
    @JsonProperty("type")
    private String type;

    @JsonProperty("event")
    private String event;

    @JsonProperty("object")
    private PaymentObject object;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaymentObject {

        @JsonProperty("id")
        private String id;          // ID платежа в ЮKassa

        @JsonProperty("status")
        private String status;      // succeeded, canceled

        @JsonProperty("metadata")
        private Metadata metadata;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Metadata {
        @JsonProperty("internal_payment_id")
        private String internalPaymentId;  // наш Payment.id
    }

    public String getYookassaPaymentId() {
        return object != null ? object.getId() : null;
    }

    public String getPaymentStatus() {
        return object != null ? object.getStatus() : null;
    }

    public String getInternalPaymentId() {
        return object != null && object.getMetadata() != null
                ? object.getMetadata().getInternalPaymentId()
                : null;
    }
}
