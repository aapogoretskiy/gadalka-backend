package ru.sapa.gadalka_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.domain.PaymentWebhookLog;
import ru.sapa.gadalka_backend.domain.type.PaymentProvider;
import ru.sapa.gadalka_backend.domain.type.WebhookStatus;
import ru.sapa.gadalka_backend.repository.PaymentWebhookLogRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Ack-сервис для надёжной обработки webhook-уведомлений от платёжных провайдеров.
 * <p>
 * Проблема которую решаем: провайдеры ожидают HTTP 200 в течение нескольких секунд.
 * Если мы обрабатываем платёж синхронно и в этот момент падает БД — мы не успеваем
 * ответить, провайдер делает ретрай, возможна двойная обработка или потеря события.
 * <p>
 * Решение — двухфазная обработка для всех провайдеров:
 * <ol>
 *   <li>{@link #acknowledge}: сохраняем сырой payload за ~1мс → отвечаем провайдеру.</li>
 *   <li>{@link #processPendingWebhooks}: @Scheduled читает PENDING записи → обрабатывает →
 *       ставит PROCESSED или FAILED.</li>
 * </ol>
 * <p>
 * Важное отличие Robokassa: Robokassa ждёт не просто HTTP 200, а текст {@code OK{InvId}}.
 * InvId читается из параметров запроса до вызова acknowledge (это мгновенно),
 * поэтому мы всё равно отвечаем сразу, а обработку откладываем на фон.
 * <p>
 * Формат rawPayload по провайдерам:
 * <ul>
 *   <li>YOOKASSA — сырой JSON-body webhook'а</li>
 *   <li>ROBOKASSA — JSON-сериализация параметров: {@code {"outSum":"...","invId":"...","signatureValue":"..."}}</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookAckService {

    private final PaymentWebhookLogRepository webhookLogRepository;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    /**
     * Фаза 1: мгновенно сохраняем payload, возвращаем управление контроллеру.
     * Транзакция короткая — только INSERT в одну таблицу.
     */
    @Transactional
    public PaymentWebhookLog acknowledge(PaymentProvider provider, String rawPayload) {
        PaymentWebhookLog webhookLog = PaymentWebhookLog.builder()
                .provider(provider)
                .rawPayload(rawPayload)
                .status(WebhookStatus.PENDING)
                .build();

        PaymentWebhookLog saved = webhookLogRepository.save(webhookLog);
        log.debug("Webhook acknowledged: id={}, provider={}", saved.getId(), provider);
        return saved;
    }

    /**
     * Удобный метод для Robokassa: сериализует параметры в JSON и сохраняет.
     */
    @Transactional
    public PaymentWebhookLog acknowledgeRobokassa(String outSum, String invId, String signatureValue) {
        try {
            String payload = objectMapper.writeValueAsString(
                    Map.of("outSum", outSum, "invId", invId, "signatureValue", signatureValue)
            );
            return acknowledge(PaymentProvider.ROBOKASSA, payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать Robokassa webhook params", e);
        }
    }

    /**
     * Фаза 2: обрабатываем накопленные PENDING webhook'и.
     * fixedDelay — задержка между концом предыдущего и началом следующего запуска.
     */
    @Scheduled(fixedDelayString = "${payment.webhook.process-delay-ms:30000}")
    public void processPendingWebhooks() {
        List<PaymentWebhookLog> pending = webhookLogRepository.findAllByStatus(WebhookStatus.PENDING);

        if (pending.isEmpty()) return;

        log.info("Обработка {} pending webhook(s)", pending.size());

        for (PaymentWebhookLog webhook : pending) {
            processSingle(webhook);
        }
    }

    /**
     * Обрабатывает один webhook. Каждый в отдельной транзакции —
     * чтобы ошибка в одном не откатила успешные.
     * Маршрутизация по провайдеру — каждый провайдер имеет свой формат payload.
     */
    @Transactional
    public void processSingle(PaymentWebhookLog webhook) {
        try {
            switch (webhook.getProvider()) {
                case YOOKASSA   -> paymentService.processYooKassaWebhook(webhook.getRawPayload());
                case ROBOKASSA  -> processRobokassaWebhook(webhook.getRawPayload());
                default         -> log.warn("Неизвестный провайдер webhook: {}", webhook.getProvider());
            }

            webhook.setStatus(WebhookStatus.PROCESSED);
            webhook.setProcessedAt(OffsetDateTime.now());
            log.info("Webhook обработан: id={}, provider={}", webhook.getId(), webhook.getProvider());

        } catch (Exception e) {
            webhook.setStatus(WebhookStatus.FAILED);
            webhook.setErrorMessage(e.getMessage());
            log.error("Ошибка обработки webhook id={}, provider={}: {}",
                    webhook.getId(), webhook.getProvider(), e.getMessage(), e);
        }

        webhookLogRepository.save(webhook);
    }

    /**
     * Десериализует JSON-payload Robokassa и передаёт в PaymentService.
     * Формат payload: {"outSum":"...","invId":"...","signatureValue":"..."}
     */
    private void processRobokassaWebhook(String rawPayload) {
        try {
            JsonNode node = objectMapper.readTree(rawPayload);
            String outSum         = node.get("outSum").asText();
            String invId          = node.get("invId").asText();
            String signatureValue = node.get("signatureValue").asText();
            paymentService.processRobokassaWebhook(outSum, invId, signatureValue);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось десериализовать Robokassa webhook payload", e);
        }
    }
}
