package ru.sapa.gadalka_backend.bot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "telegram.bot.enabled", havingValue = "true")
public class GadalkaTelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final TelegramClient telegramClient;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.app-url}")
    private String appUrl;

    public GadalkaTelegramBot(TelegramClient telegramClient) {
        this.telegramClient = telegramClient;
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
        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        String text = update.getMessage().getText();
        long chatId = update.getMessage().getChatId();

        if ("/start".equals(text) || text.startsWith("/start ")) {
            sendWelcomeMessage(chatId);
        }
    }

    private void sendWelcomeMessage(long chatId) {
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text("🔮 Открыть Гадалку")
                .webApp(new WebAppInfo(appUrl))
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
            log.info("Приветственное сообщение отправлено: chatId={}", chatId);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки приветственного сообщения, chatId={}: {}", chatId, e.getMessage(), e);
        }
    }
}
