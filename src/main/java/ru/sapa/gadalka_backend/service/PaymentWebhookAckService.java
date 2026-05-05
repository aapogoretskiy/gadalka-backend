package ru.sapa.gadalka_backend.service;

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

/**
 * Ack-сервис для надёжной обработки webhook-уведомлений от ЮKassa.
 * <p>
 * Проблема которую решаем:
 * ЮKassa ожидает HTTP 200 в течение нескольких секунд. Если ответ не пришёл —
 * она ретраит. Если же мы попытаемся обработать платёж синхронно в контроллере
 * и в этот момент упадёт БД или сломается бизнес-логика — мы не успеем ответить,
 * ЮKassa будет ретраить бесконечно или пометит webhook как доставленный при
 * следующем ретрае, а мы потеряем событие.
 * <p>
 * Решение — двухфазная обработка:
 * 1. acknowledge(): сохраняем сырой payload за ~1мс → HTTP 200 → ЮKassa довольна.
 * 2. processPendingWebhooks(): @Scheduled читает PENDING записи → обрабатывает →
 *    ставит PROCESSED или FAILED. Если FAILED — запись остаётся, можно перезапустить вручную.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookAckService {

    private final PaymentWebhookLogRepository webhookLogRepository;
    private final PaymentService paymentService;

    /**
     *р Фаза 1: мгновенно сохраняем сырой payload, возвращаем управление контроллеру.
     * Транзакция короткая — только INSERT в одну таблицу.
     */
    @Transactional
    public PaymentWebhookLog acknowledge(PaymentProvider provider, String rawPayload) {
        PaymentWebhookLog log = PaymentWebhookLog.builder()
                .provider(provider)
                .rawPayload(rawPayload)
                .status(WebhookStatus.PENDING)
                .build();

        PaymentWebhookLog saved = webhookLogRepository.save(log);
        PaymentWebhookAckService.log.debug("Webhook acknowledged: id={}, provider={}", saved.getId(), provider);
        return saved;
    }

    /**
     * Фаза 2: обрабатываем накопленные PENDING webhook'и.
     * fixedDelay — задержка между концом предыдущего и началом следующего запуска.
     * Используем fixedDelay (а не fixedRate) чтобы не накапливать параллельные запуски
     * если обработка займёт больше времени чем интервал.
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
     */
    @Transactional
    public void processSingle(PaymentWebhookLog webhook) {
        try {
            paymentService.processYooKassaWebhook(webhook.getRawPayload());

            webhook.setStatus(WebhookStatus.PROCESSED);
            webhook.setProcessedAt(OffsetDateTime.now());
            log.info("Webhook обработан: id={}", webhook.getId());

        } catch (Exception e) {
            webhook.setStatus(WebhookStatus.FAILED);
            webhook.setErrorMessage(e.getMessage());
            log.error("Ошибка обработки webhook id={}: {}", webhook.getId(), e.getMessage(), e);
        }

        webhookLogRepository.save(webhook);
    }
}
