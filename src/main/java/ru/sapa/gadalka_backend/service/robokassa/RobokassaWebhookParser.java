package ru.sapa.gadalka_backend.service.robokassa;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Парсер и валидатор webhook-уведомлений от Robokassa (ResultURL).
 * <p>
 * Робокасса вызывает ResultURL методом POST с параметрами формы:
 * OutSum, InvId, Fee, SignatureValue, EMail, и т.д.
 * <p>
 * Проверка подписи: MD5(OutSum:InvId:Пароль#2[:Shp_*])
 * Мы не используем Shp_* параметры, поэтому формула упрощается до:
 * MD5(OutSum:InvId:Пароль#2)
 * <p>
 * После успешной проверки необходимо ответить текстом "OK{InvId}" (например, "OK42").
 * Любой другой ответ или HTTP != 200 заставит Робокассу повторять запрос.
 */
@Slf4j
@Component
public class RobokassaWebhookParser {

    private final String password2;

    public RobokassaWebhookParser(
            @Value("${robokassa.password2}") String password2) {
        this.password2 = password2;
    }

    /**
     * Извлекает наш внутренний ID платежа из параметров webhook.
     * InvId — это тот самый ID, который мы передали при создании платежа.
     */
    public Long extractInvId(String invId) {
        try {
            return Long.parseLong(invId);
        } catch (NumberFormatException e) {
            log.error("Некорректный InvId в webhook Robokassa: '{}'", invId);
            throw new IllegalArgumentException("Некорректный InvId: " + invId, e);
        }
    }

    /**
     * Проверяет подпись webhook.
     * Формула: MD5(OutSum:InvId:Пароль#2) — регистр не важен при сравнении.
     *
     * @param outSum         сумма платежа (строка как пришла от Робокассы)
     * @param invId          номер заказа
     * @param signatureValue подпись от Робокассы
     * @return true если подпись корректна
     */
    public boolean isSignatureValid(String outSum, String invId, String signatureValue) {
        // Формула webhook: MD5(OutSum:InvId:Пароль#2) — без MerchantLogin!
        // Отличается от формулы создания платежа (там есть MerchantLogin в начале).
        String raw = String.join(":", outSum, invId, password2);
        String expected = RobokassaClient.md5(raw);

        boolean valid = expected.equalsIgnoreCase(signatureValue);
        if (!valid) {
            log.warn("Невалидная подпись Robokassa webhook: expected={}, got={}",
                    expected, signatureValue);
        }
        return valid;
    }

}
