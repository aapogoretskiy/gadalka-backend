package ru.sapa.gadalka_backend.service.yookassa.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Сумма платежа в формате ЮKassa.
 * value — строка с двумя знаками после запятой ("99.00"), currency — ISO-код валюты.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class YooKassaAmount {

    @JsonProperty("value")
    private String value;

    @JsonProperty("currency")
    private String currency;

    /**
     * Конвертирует копейки в строку для ЮKassa (9900 → "99.00").
     */
    public static YooKassaAmount fromMinorUnits(int minorUnits, String currency) {
        String value = String.format("%.2f", minorUnits / 100.0);
        return new YooKassaAmount(value, currency);
    }
}
