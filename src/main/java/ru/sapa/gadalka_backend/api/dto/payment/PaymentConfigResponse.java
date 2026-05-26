package ru.sapa.gadalka_backend.api.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Конфигурация платёжной системы для фронтенда.
 * Позволяет переключать провайдера рублёвых платежей без передеплоя фронта.
 */
@Getter
@AllArgsConstructor
public class PaymentConfigResponse {

    /**
     * Активный провайдер рублёвых платежей.
     * Возможные значения: "robokassa", "yookassa"
     * Управляется через переменную окружения PAYMENT_RUB_PROVIDER.
     */
    private String rubProvider;
}
