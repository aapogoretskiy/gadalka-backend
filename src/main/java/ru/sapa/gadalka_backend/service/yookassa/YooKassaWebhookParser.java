package ru.sapa.gadalka_backend.service.yookassa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.sapa.gadalka_backend.service.yookassa.dto.YooKassaWebhookPayload;

/**
 * Парсит сырой JSON webhook-payload от ЮKassa в типизированный объект.
 * Вынесен в отдельный класс чтобы логику парсинга можно было тестировать отдельно.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YooKassaWebhookParser {

    private final ObjectMapper objectMapper;

    /**
     * Парсит сырой JSON в YooKassaWebhookPayload.
     * Кидает IllegalArgumentException если JSON невалидный.
     */
    public YooKassaWebhookPayload parse(String rawJson) {
        try {
            return objectMapper.readValue(rawJson, YooKassaWebhookPayload.class);
        } catch (JsonProcessingException e) {
            log.error("Не удалось распарсить webhook от ЮKassa: {}", e.getMessage());
            throw new IllegalArgumentException("Невалидный webhook payload от ЮKassa", e);
        }
    }

    /**
     * Проверяет, является ли событие успешным платежом.
     */
    public boolean isPaymentSucceeded(YooKassaWebhookPayload payload) {
        return "payment.succeeded".equals(payload.getEvent());
    }

    /**
     * Проверяет, является ли событие отменой платежа.
     */
    public boolean isPaymentCancelled(YooKassaWebhookPayload payload) {
        return "payment.canceled".equals(payload.getEvent());
    }
}
