package ru.sapa.gadalka_backend.service.robokassa;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Клиент Robokassa — формирует HTML-страницу с автосабмит POST-формой для оплаты.
 * <p>
 * Robokassa требует передачи номенклатуры (Receipt) для фискализации по 54-ФЗ.
 * Receipt обязательно участвует в подписи и должен передаваться через POST.
 * <p>
 * Подпись для создания: MD5(MerchantLogin:OutSum:InvId:Receipt:Пароль#1)
 * где Receipt — URL-encoded JSON (тот же формат что браузер передаёт при сабмите формы).
 * <p>
 * Подпись для верификации webhook: MD5(OutSum:InvId:Пароль#2) — см. {@link RobokassaWebhookParser}
 */
@Slf4j
@Component
public class RobokassaClient {

    private static final String PAYMENT_URL = "https://auth.robokassa.ru/Merchant/Index.aspx";

    private final String merchantLogin;
    private final String password1;
    private final boolean testMode;
    private final String failUrl;

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
     * @return HTML-страница с автосабмит формой
     */
    public String buildPaymentFormHtml(Long paymentId, int amountKopecks, String productName) {
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
        String signature = buildSignature(merchantLogin, outSum, invId, urlEncodedReceipt, password1);

        log.info("Robokassa форма сформирована: internalId={}, outSum={}, testMode={}",
                paymentId, outSum, testMode);

        return buildHtml(outSum, invId, description, urlEncodedReceipt, signature);
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

    private static String escapeHtml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String buildHtml(String outSum, String invId, String description,
                              String urlEncodedReceipt, String signature) {
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
