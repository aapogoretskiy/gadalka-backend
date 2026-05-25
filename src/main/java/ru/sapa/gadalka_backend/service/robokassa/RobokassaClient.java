package ru.sapa.gadalka_backend.service.robokassa;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Клиент Robokassa — формирует подписанную ссылку на страницу оплаты.
 * <p>
 * В отличие от ЮKassa, у Робокассы нет server-to-server REST API для создания платежа.
 * Вместо этого мы строим URL с параметрами и подписью, пользователь переходит по нему
 * напрямую — Робокасса показывает страницу оплаты.
 * <p>
 * Подпись для создания: MD5(MerchantLogin:OutSum:InvId:Пароль#1)
 * Подпись для верификации webhook: MD5(OutSum:InvId:Пароль#2) — см. {@link RobokassaWebhookParser}
 */
@Slf4j
@Component
public class RobokassaClient {

    private static final String PAYMENT_URL = "https://auth.robokassa.ru/Merchant/Index.aspx";

    private final String merchantLogin;
    private final String password1;
    private final boolean testMode;

    public RobokassaClient(
            @Value("${robokassa.merchant-login}") String merchantLogin,
            @Value("${robokassa.password1}") String password1,
            @Value("${robokassa.test-mode:false}") boolean testMode) {

        this.merchantLogin = merchantLogin;
        this.password1 = password1;
        this.testMode = testMode;

        log.info("Robokassa клиент инициализирован (merchantLogin={}, testMode={})",
                merchantLogin, testMode);
    }

    /**
     * Формирует URL страницы оплаты Robokassa.
     *
     * @param internalPaymentId наш внутренний ID платежа (используется как InvId)
     * @param amountKopecks     сумма в копейках (9900 = 99₽)
     * @param description       описание платежа (отображается пользователю)
     * @return URL для редиректа пользователя
     */
    public String buildPaymentUrl(Long internalPaymentId, int amountKopecks, String description) {
        // Робокасса принимает рубли с 2 знаками после запятой: 9900 коп → "99.00"
        String outSum = kopecksToRubles(amountKopecks);
        String invId = String.valueOf(internalPaymentId);

        String signature = buildSignature(merchantLogin, outSum, invId, password1);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(PAYMENT_URL)
                .queryParam("MerchantLogin", merchantLogin)
                .queryParam("OutSum", outSum)
                .queryParam("InvId", invId)
                .queryParam("Description", description)
                .queryParam("SignatureValue", signature);

        // IsTest передаём ТОЛЬКО в тестовом режиме — в боевом параметр не нужен
        if (testMode) {
            builder.queryParam("IsTest", 1);
        }

        String url = builder.build().toUriString();

        log.info("Robokassa URL сформирован: internalId={}, outSum={}, testMode={}",
                internalPaymentId, outSum, testMode);

        return url;
    }

    /**
     * Вычисляет MD5-подпись для запроса на оплату.
     * Формула: MD5(MerchantLogin:OutSum:InvId:Пароль#1)
     */
    static String buildSignature(String merchantLogin, String outSum,
                                 String invId, String password) {
        String raw = String.join(":", merchantLogin, outSum, invId, password);
        return md5(raw);
    }

    /**
     * Конвертирует копейки в строку рублей с двумя знаками после запятой.
     * Пример: 9900 → "99.00", 14900 → "149.00"
     */
    static String kopecksToRubles(int kopecks) {
        return BigDecimal.valueOf(kopecks)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    /**
     * Вычисляет MD5-хэш строки в нижнем регистре (как требует Робокасса).
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
}
