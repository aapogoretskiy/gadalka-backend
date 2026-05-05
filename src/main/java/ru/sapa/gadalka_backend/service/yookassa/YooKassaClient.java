package ru.sapa.gadalka_backend.service.yookassa;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import ru.sapa.gadalka_backend.service.yookassa.dto.YooKassaAmount;
import ru.sapa.gadalka_backend.service.yookassa.dto.YooKassaCreatePaymentRequest;
import ru.sapa.gadalka_backend.service.yookassa.dto.YooKassaPaymentResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * HTTP-клиент для работы с ЮKassa API.
 * <p>
 * Авторизация: HTTP Basic Auth (shopId:secretKey).
 * Идемпотентность: каждый запрос на создание платежа передаёт Idempotence-Key
 * (наш внутренний paymentId) — защита от дублирования при повторном запросе.
 */
@Slf4j
@Component
public class YooKassaClient {

    private final WebClient webClient;
    private final String returnUrl;

    public YooKassaClient(
            @Value("${yookassa.shop-id}") String shopId,
            @Value("${yookassa.secret-key}") String secretKey,
            @Value("${yookassa.api-url}") String apiUrl,
            @Value("${yookassa.return-url}") String returnUrl) {

        this.returnUrl = returnUrl;

        // Basic Auth заголовок: Base64(shopId:secretKey)
        String credentials = shopId + ":" + secretKey;
        String basicAuth = "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        this.webClient = WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, basicAuth)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Создаёт платёж в ЮKassa и возвращает URL для редиректа пользователя.
     *
     * @param internalPaymentId  наш внутренний ID платежа (используется как idempotency key)
     * @param amountMinor        сумма в копейках
     * @param description        описание платежа (отображается пользователю)
     */
    public YooKassaPaymentResponse createPayment(Long internalPaymentId,
                                                  int amountMinor,
                                                  String description) {
        YooKassaCreatePaymentRequest request = YooKassaCreatePaymentRequest.builder()
                .amount(YooKassaAmount.fromMinorUnits(amountMinor, "RUB"))
                .confirmation(YooKassaCreatePaymentRequest.Confirmation.builder()
                        .type("redirect")
                        .returnUrl(returnUrl)
                        .build())
                .description(description)
                .metadata(YooKassaCreatePaymentRequest.Metadata.builder()
                        .internalPaymentId(String.valueOf(internalPaymentId))
                        .build())
                .capture(true)  // автоматически подтверждать платёж (не двухстадийный)
                .build();

        log.info("Создание платежа в ЮKassa: internalPaymentId={}, amount={}коп", internalPaymentId, amountMinor);

        try {
            return webClient.post()
                    .uri("/payments")
                    // Idempotence-Key: если запрос повторится — ЮKassa вернёт тот же платёж
                    .header("Idempotence-Key", String.valueOf(internalPaymentId))
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(YooKassaPaymentResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Ошибка ЮKassa API: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("Ошибка создания платежа в ЮKassa: " + e.getMessage(), e);
        }
    }
}
