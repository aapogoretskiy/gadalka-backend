package ru.sapa.gadalka_backend.bot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerPreCheckoutQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.payments.PreCheckoutQuery;
import org.telegram.telegrambots.meta.api.objects.payments.SuccessfulPayment;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.sapa.gadalka_backend.service.PaymentService;
import ru.sapa.gadalka_backend.service.ReferralService;
import ru.sapa.gadalka_backend.service.stars.TelegramStarsService;

import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "telegram.bot.enabled", havingValue = "true")
public class GadalkaTelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final TelegramClient telegramClient;
    private final ReferralService referralService;
    private final PaymentService paymentService;
    private final TelegramStarsService starsService;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.app-url}")
    private String appUrl;

    public GadalkaTelegramBot(TelegramClient telegramClient,
                              ReferralService referralService,
                              PaymentService paymentService,
                              TelegramStarsService starsService) {
        this.telegramClient = telegramClient;
        this.referralService = referralService;
        this.paymentService = paymentService;
        this.starsService = starsService;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        // Обычные текстовые сообщения
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            if (text.equals("/start") || text.startsWith("/start ")) {
                String referralCode = extractReferralCode(text);
                if (referralCode != null) {
                    referralService.recordBotEntry(chatId, referralCode);
                }
                sendWelcomeMessage(chatId, referralCode);
            }
            return;
        }

        // PreCheckoutQuery — Telegram спрашивает "можно ли провести платёж?"
        // Мы ОБЯЗАНЫ ответить в течение 10 секунд, иначе платёж будет отклонён.
        if (update.hasPreCheckoutQuery()) {
            handlePreCheckoutQuery(update.getPreCheckoutQuery());
            return;
        }

        // SuccessfulPayment — платёж Stars прошёл успешно
        if (update.hasMessage() && update.getMessage().hasSuccessfulPayment()) {
            handleSuccessfulPayment(update.getMessage().getSuccessfulPayment());
        }
    }

    /**
     * Обрабатывает запрос на предварительную проверку перед оплатой Stars.
     * Telegram ждёт ответа максимум 10 секунд — отвечаем быстро.
     * В нашем случае всегда разрешаем — реальная проверка происходит после SuccessfulPayment.
     */
    private void handlePreCheckoutQuery(PreCheckoutQuery query) {
        log.info("PreCheckoutQuery: id={}, userId={}, payload={}",
                query.getId(), query.getFrom().getId(), query.getInvoicePayload());
        try {
            telegramClient.execute(AnswerPreCheckoutQuery.builder()
                    .preCheckoutQueryId(query.getId())
                    .ok(true)
                    .build());
        } catch (TelegramApiException e) {
            log.error("Ошибка ответа на PreCheckoutQuery id={}: {}", query.getId(), e.getMessage(), e);
        }
    }

    /**
     * Обрабатывает успешный Stars-платёж.
     * telegramPaymentChargeId — уникальный ID транзакции Telegram (идемпотентность).
     * invoicePayload — наш внутренний Payment.id, который мы передали при создании инвойса.
     */
    private void handleSuccessfulPayment(SuccessfulPayment successfulPayment) {
        Long internalPaymentId = starsService.extractInternalPaymentId(successfulPayment);
        String chargeId = starsService.extractTelegramChargeId(successfulPayment);

        log.info("SuccessfulPayment Stars: internalPaymentId={}, chargeId={}", internalPaymentId, chargeId);

        try {
            paymentService.processStarsSuccess(internalPaymentId, chargeId);
        } catch (Exception e) {
            // Логируем, но не падаем — деньги уже списаны, нужно разобраться вручную
            log.error("Ошибка обработки Stars платежа: internalPaymentId={}, chargeId={}, error={}",
                    internalPaymentId, chargeId, e.getMessage(), e);
        }
    }

    /**
     * Извлекает реферальный код из команды вида "/start telegram_channel1".
     * Возвращает null, если код отсутствует или пустой.
     * Метод публичный для удобства тестирования.
     */
    public String extractReferralCode(String startCommand) {
        if (startCommand == null || !startCommand.startsWith("/start ")) return null;
        String code = startCommand.substring("/start ".length()).trim();
        return code.isEmpty() ? null : code;
    }

    private void sendWelcomeMessage(long chatId, String referralCode) {
        // Чтобы Mini App получает start_param в initData при открытии
        String webAppUrl = (referralCode != null && !referralCode.isBlank())
                ? appUrl + "?startapp=" + referralCode
                : appUrl;

        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text("🔮 Открыть Гадалку")
                .webApp(new WebAppInfo(webAppUrl))
                .build();

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboard(List.of(new InlineKeyboardRow(button)))
                .build();

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("✨ *Добро пожаловать в Гадалку!*\n\n" +
                      "Здесь карты Таро раскроют тайны вашего прошлого, настоящего и будущего.\n\n" +
                      "Нажмите кнопку ниже, чтобы открыть приложение и получить свой персональный расклад 🌙")
                .parseMode("Markdown")
                .replyMarkup(keyboard)
                .build();

        try {
            telegramClient.execute(message);
            log.info("Приветственное сообщение отправлено: chatId={}, referralCode={}", chatId, referralCode);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки приветственного сообщения, chatId={}: {}", chatId, e.getMessage(), e);
        }
    }
}
