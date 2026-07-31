package ru.sapa.gadalka_backend.service.robokassa;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Клиент Robokassa — формирует HTML-страницу с автосабмит POST-формой для оплаты,
 * а также инициирует рекуррентные (дочерние) списания напрямую с бэкенда.
 * <p>
 * Robokassa требует передачи номенклатуры (Receipt) для фискализации по 54-ФЗ
 * (для самозанятых — интеграция «Робочеки СМЗ» с «Мой налог», тот же параметр Receipt).
 * Receipt обязательно участвует в подписи и должен передаваться через POST.
 * <p>
 * Подпись для создания: MD5(MerchantLogin:OutSum:InvId:Receipt:Пароль#1)
 * где Receipt — URL-encoded JSON (тот же формат что браузер передаёт при сабмите формы).
 * Та же формула используется и для рекуррентных (дочерних) списаний — см. {@link #chargeRecurring}.
 * <p>
 * Подпись для верификации webhook: MD5(OutSum:InvId:Пароль#2) — см. {@link RobokassaWebhookParser}
 */
@Slf4j
@Component
public class RobokassaClient {

    private static final String PAYMENT_URL = "https://auth.robokassa.ru/Merchant/Index.aspx";
    private static final String RECURRING_URL = "/Merchant/Recurring";

    private final String merchantLogin;
    private final String password1;
    private final boolean testMode;
    private final String failUrl;
    private final WebClient webClient;

    public RobokassaClient(
            @Value("${robokassa.merchant-login}") String merchantLogin,
            @Value("${robokassa.password1}") String password1,
            @Value("${robokassa.test-mode:false}") boolean testMode,
            @Value("${telegram.bot.app-url}") String appUrl) {

        this.merchantLogin = merchantLogin;
        this.password1 = password1;
        this.testMode = testMode;
        // FailURL — куда Robokassa редиректит браузер при явном отказе от оплаты.
        // Сама Robokassa допишет к нему ?InvId=...&OutSum=... при редиректе.
        // ВАЖНО: чтобы это реально сработало, в личном кабинете Robokassa нужно убедиться,
        // что используется URL, переданный в запросе, а не заданный в кабинете по умолчанию
        // (см. PaymentController#robokassaFail) — у ResultURL, например, сейчас используется
        // именно кабинетная настройка, в форме он не передаётся вовсе.
        String base = appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
        this.failUrl = base + "/api/v1/payments/robokassa/fail";

        // Для рекуррентных списаний (chargeRecurring) — прямой серверный POST,
        // без браузера и без промежуточной HTML-страницы.
        this.webClient = WebClient.builder()
                .baseUrl("https://auth.robokassa.ru")
                .build();

        log.info("Robokassa клиент инициализирован (merchantLogin={}, testMode={}, failUrl={})", merchantLogin, testMode, failUrl);
    }

    /**
     * Строит HTML-страницу с автосабмит POST-формой для перенаправления на Robokassa.
     * <p>
     * Пользователь открывает нашу промежуточную страницу — JavaScript мгновенно
     * сабмитит форму → браузер делает POST на Robokassa → пользователь видит страницу оплаты.
     *
     * @param paymentId     наш внутренний ID платежа (InvId)
     * @param amountKopecks сумма в копейках (19900 = 199₽)
     * @param productName   название продукта для Receipt и Description
     * @param recurring     true — это первый (материнский) платёж подписки с согласием
     *                      пользователя на автопродление, в форму добавляется Recurring=true,
     *                      и Robokassa разрешит нам в будущем списывать по этому InvId
     *                      как по PreviousInvoiceID (см. {@link #chargeRecurring})
     * @return HTML-страница с автосабмит формой
     */
    public String buildPaymentFormHtml(Long paymentId, int amountKopecks, String productName, boolean recurring) {
        String outSum      = kopecksToRubles(amountKopecks);
        String invId       = String.valueOf(paymentId);
        String description = "Покупка: " + productName;

        // Строим Receipt JSON для фискализации (СМЗ: без НДС, тип — услуга)
        String receiptJson = buildReceiptJson(productName, outSum);

        // URL-кодируем Receipt — именно этот encoded вариант участвует в подписи.
        // Браузер при сабмите формы тоже URL-кодирует значения полей (form url-encoding),
        // поэтому Robokassa получит тот же encoded Receipt что мы использовали в подписи.
        String urlEncodedReceipt = urlEncode(receiptJson);

        // Подпись с Receipt: MD5(MerchantLogin:OutSum:InvId:Receipt:Пароль#1)
        // Recurring в формулу подписи не входит — в документации Robokassa он
        // просто дополнительное поле формы, не участвующее в контрольной сумме.
        String signature = buildSignature(merchantLogin, outSum, invId, urlEncodedReceipt, password1);

        log.info("Robokassa форма сформирована: internalId={}, outSum={}, recurring={}, testMode={}", paymentId, outSum, recurring, testMode);

        return buildHtml(outSum, invId, description, urlEncodedReceipt, signature, recurring);
    }

    /**
     * Инициирует рекуррентное (дочернее) списание — прямой серверный POST на
     * {@code Merchant/Recurring}, без участия пользователя и без промежуточной
     * HTML-страницы (в отличие от {@link #buildPaymentFormHtml}).
     * <p>
     * Используется только для автопродления подписки, см.
     * {@code PaymentService#renewSubscription}.
     * <p>
     * Ответ "OK..." означает, что Robokassa приняла операцию В ОБРАБОТКУ —
     * это НЕ означает, что деньги уже списаны. Реальный результат по-прежнему
     * приходит асинхронно на ResultURL, как и для обычного платежа
     * (см. {@code PaymentService#processRobokassaWebhook}) — этот метод только
     * запускает списание и возвращает, принята ли заявка.
     * <p>
     * ВАЖНО (пока не подтверждено тестовым платежом): в документации Robokassa
     * пример дочернего запроса не показывает Receipt — только отсылку к разделу
     * «Фискализация». Комбинация Receipt + PreviousInvoiceID в одном запросе
     * должна быть проверена, как только Robokassa включит рекуррентность на аккаунте.
     *
     * @param invoiceId         id нового платежа (нашего Payment) — станет InvoiceID
     * @param previousInvoiceId id материнского платежа цепочки подписки — PreviousInvoiceID
     * @param amountKopecks     сумма списания в копейках
     * @param productName       название для Receipt/Description
     * @return true, если Robokassa приняла операцию (ответ начинается с "OK")
     */
    public boolean chargeRecurring(Long invoiceId, Long previousInvoiceId, int amountKopecks, String productName) {
        String outSum      = kopecksToRubles(amountKopecks);
        String invId       = String.valueOf(invoiceId);
        String description = "Продление подписки: " + productName;

        String receiptJson = buildReceiptJson(productName, outSum);
        String urlEncodedReceipt = urlEncode(receiptJson);
        // Та же формула, что и для родительского платежа: MD5(MerchantLogin:OutSum:InvId:Receipt:Пароль#1).
        // PreviousInvoiceID в подпись не входит — Robokassa не указывает его в формуле подписи.
        String signature = buildSignature(merchantLogin, outSum, invId, urlEncodedReceipt, password1);

        // Тело собираем вручную (не через стандартный form-сериализатор WebClient) —
        // Receipt уже url-encoded один раз для подписи, и, как и в браузерном сценарии
        // buildPaymentFormHtml, значение должно быть закодировано ЕЩЁ раз при попадании
        // в тело запроса application/x-www-form-urlencoded, чтобы после стандартного
        // form-декодирования на стороне Robokassa оно совпало с тем, что участвовало в подписи.
        StringBuilder body = new StringBuilder();
        appendFormField(body, "MerchantLogin", merchantLogin);
        appendFormField(body, "InvoiceID", invId);
        appendFormField(body, "PreviousInvoiceID", String.valueOf(previousInvoiceId));
        appendFormField(body, "Description", description);
        appendFormField(body, "Receipt", urlEncodedReceipt);
        appendFormField(body, "OutSum", outSum);
        appendFormField(body, "SignatureValue", signature);
        if (testMode) {
            appendFormField(body, "IsTest", "1");
        }
        String requestBody = body.substring(1); // убираем лидирующий '&'

        log.info("Robokassa: рекуррентное списание: invoiceId={}, previousInvoiceId={}, outSum={}, testMode={}", invoiceId, previousInvoiceId, outSum, testMode);

        String rawResponse;
        try {
            rawResponse = webClient.post()
                    .uri(RECURRING_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Ошибка запроса рекуррентного списания в Robokassa: invoiceId={}, status={}, body={}", invoiceId, e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.error("Не удалось выполнить запрос рекуррентного списания в Robokassa: invoiceId={}", invoiceId, e);
            return false;
        }
        boolean accepted = rawResponse != null && rawResponse.trim().toUpperCase().startsWith("OK");
        if (!accepted) {
            log.warn("Robokassa отклонила запрос на рекуррентное списание: invoiceId={}, response={}", invoiceId, rawResponse);
        }
        return accepted;
    }

    /**
     * Строит JSON-номенклатуру для чека (Receipt).
     * Для СМЗ: без НДС (tax=none), тип позиции — услуга (service), полная оплата.
     *
     * @param productName название услуги (попадёт в чек)
     * @param sum         сумма в рублях (строка вида "199.00")
     */
    public String buildReceiptJson(String productName, String sum) {
        String safeName = productName.replace("\\", "\\\\").replace("\"", "\\\"");
        return String.format(
                "{\"items\":[{\"name\":\"%s\",\"quantity\":1,\"sum\":%s," +
                "\"payment_method\":\"full_payment\",\"payment_object\":\"service\",\"tax\":\"none\"}]}",
                safeName, sum
        );
    }

    /**
     * Вычисляет MD5-подпись с Receipt.
     * Формула: MD5(MerchantLogin:OutSum:InvId:Receipt:Пароль#1)
     * Receipt передаётся URL-encoded — тот же формат что браузер шлёт в POST-теле формы.
     */
    static String buildSignature(String merchantLogin, String outSum, String invId,
                                 String urlEncodedReceipt, String password) {
        String raw = String.join(":", merchantLogin, outSum, invId, urlEncodedReceipt, password);
        return md5(raw);
    }

    /**
     * Конвертирует копейки в строку рублей с двумя знаками после запятой.
     * Пример: 19900 → "199.00"
     */
    public static String kopecksToRubles(int kopecks) {
        return BigDecimal.valueOf(kopecks)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    /**
     * Вычисляет MD5-хэш строки в верхнем регистре (как требует Robokassa).
     */
    static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 недоступен", e);
        }
    }

    // ── Приватные вспомогательные методы ────────────────────────────────────────

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Добавляет "&name=urlEncodedValue" к телу application/x-www-form-urlencoded запроса */
    private static void appendFormField(StringBuilder sb, String name, String value) {
        sb.append('&').append(name).append('=').append(urlEncode(value));
    }

    private static String escapeHtml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String buildHtml(String outSum, String invId, String description, String urlEncodedReceipt, String signature, boolean recurring) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head>")
          .append("<meta charset=\"UTF-8\">")
          .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
          .append("<title>Переход к оплате...</title>")
          .append("</head><body>")
          .append("<form id=\"f\" method=\"POST\" action=\"").append(PAYMENT_URL).append("\">")
          .append(hiddenField("MerchantLogin", merchantLogin))
          .append(hiddenField("OutSum",         outSum))
          .append(hiddenField("InvId",          invId))
          .append(hiddenField("Description",    description))
          .append(hiddenField("Receipt",        urlEncodedReceipt))
          .append(hiddenField("SignatureValue",  signature))
          .append(hiddenField("FailURL",         failUrl));

        if (recurring) {
            // Помечает платёж как материнский для будущих рекуррентных списаний —
            // без этого Robokassa не разрешит использовать этот InvId как PreviousInvoiceID.
            sb.append(hiddenField("Recurring", "true"));
        }

        if (testMode) {
            sb.append(hiddenField("IsTest", "1"));
        }

        sb.append("</form>")
          .append("<script>document.getElementById('f').submit();</script>")
          .append("</body></html>");

        return sb.toString();
    }

    private static String hiddenField(String name, String value) {
        return String.format("<input type=\"hidden\" name=\"%s\" value=\"%s\"/>",
                escapeHtml(name), escapeHtml(value));
    }
}
