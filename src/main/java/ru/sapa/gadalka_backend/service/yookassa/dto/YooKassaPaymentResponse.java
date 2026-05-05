package ru.sapa.gadalka_backend.service.yookassa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Ответ ЮKassa на создание/получение платежа.
 * @JsonIgnoreProperties — игнорируем поля которые нам не нужны,
 * чтобы не ломаться при добавлении новых полей в API ЮKassa.
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class YooKassaPaymentResponse {

    @JsonProperty("id")
    private String id;            // ID платежа в ЮKassa

    @JsonProperty("status")
    private String status;        // pending, waiting_for_capture, succeeded, canceled

    @JsonProperty("confirmation")
    private Confirmation confirmation;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Confirmation {
        @JsonProperty("confirmation_url")
        private String confirmationUrl;  // URL на который редиректим пользователя
    }

    public String getConfirmationUrl() {
        return confirmation != null ? confirmation.getConfirmationUrl() : null;
    }
}
