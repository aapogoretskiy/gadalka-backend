package ru.sapa.gadalka_backend.referral;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.sapa.gadalka_backend.bot.GadalkaTelegramBot;
import ru.sapa.gadalka_backend.service.PaymentService;
import ru.sapa.gadalka_backend.service.ReferralService;
import ru.sapa.gadalka_backend.service.stars.TelegramStarsService;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Тесты обработки реферальных deep-link в боте.
 *
 * <p>Проверяем три сценария:
 * <ol>
 *   <li>Обычный /start без параметра — реферал не фиксируется, URL кнопки чистый</li>
 *   <li>/start telegram_channel1 — реферал фиксируется, URL кнопки содержит startapp=</li>
 *   <li>Разные реферальные коды передаются корректно</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class GadalkaBotReferralTest {

    @Mock private TelegramClient telegramClient;
    @Mock private ReferralService referralService;
    @Mock private PaymentService paymentService;
    @Mock private TelegramStarsService telegramStarsService;

    private GadalkaTelegramBot bot;

    private static final String APP_URL = "https://app.example.com";
    private static final long CHAT_ID = 111222333L;

    @BeforeEach
    void setUp() throws Exception {
        bot = new GadalkaTelegramBot(telegramClient, referralService, paymentService, telegramStarsService);
        setField(bot, "botToken", "test_token");
        setField(bot, "appUrl", APP_URL);
    }

    // ── extractReferralCode ───────────────────────────────────────────────────

    @Nested
    @DisplayName("extractReferralCode (парсинг команды)")
    class ExtractReferralCode {

        @Test
        @DisplayName("/start telegram_channel1 → 'telegram_channel1'")
        void startWithCode_returnsCode() {
            assertThat(bot.extractReferralCode("/start telegram_channel1"))
                    .isEqualTo("telegram_channel1");
        }

        @Test
        @DisplayName("/start tiktok_video1 → 'tiktok_video1'")
        void startWithTiktokCode_returnsCode() {
            assertThat(bot.extractReferralCode("/start tiktok_video1"))
                    .isEqualTo("tiktok_video1");
        }

        @Test
        @DisplayName("/start (без параметра) → null")
        void startWithoutCode_returnsNull() {
            assertThat(bot.extractReferralCode("/start")).isNull();
        }

        @Test
        @DisplayName("/start   (пробелы) → null")
        void startWithOnlySpaces_returnsNull() {
            assertThat(bot.extractReferralCode("/start   ")).isNull();
        }

        @Test
        @DisplayName("null → null")
        void nullCommand_returnsNull() {
            assertThat(bot.extractReferralCode(null)).isNull();
        }

        @Test
        @DisplayName("Пробелы вокруг кода обрезаются")
        void trailingSpacesTrimmed() {
            assertThat(bot.extractReferralCode("/start   my_code  "))
                    .isEqualTo("my_code");
        }
    }

    // ── consume: обычный /start ───────────────────────────────────────────────

    @Nested
    @DisplayName("Обычный /start (без реферала)")
    class PlainStart {

        @Test
        @DisplayName("ReferralService НЕ вызывается при /start без параметра")
        void plainStart_referralServiceNotCalled() throws Exception {
            bot.consume(buildUpdate(CHAT_ID, "/start"));
            verifyNoInteractions(referralService);
        }

        @Test
        @DisplayName("URL кнопки = чистый appUrl (без startapp=)")
        void plainStart_buttonUrlIsCleanAppUrl() throws Exception {
            bot.consume(buildUpdate(CHAT_ID, "/start"));

            ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
            verify(telegramClient).execute(captor.capture());

            String webAppUrl = extractWebAppUrl(captor.getValue());
            assertThat(webAppUrl).isEqualTo(APP_URL);
            assertThat(webAppUrl).doesNotContain("startapp");
        }
    }

    // ── consume: /start с реферальным кодом ──────────────────────────────────

    @Nested
    @DisplayName("/start с реферальным кодом (deep-link)")
    class ReferralStart {

        @Test
        @DisplayName("recordBotEntry вызывается с верными telegramId и кодом")
        void recordBotEntryCalled() throws Exception {
            bot.consume(buildUpdate(CHAT_ID, "/start telegram_channel1"));

            verify(referralService).recordBotEntry(CHAT_ID, "telegram_channel1");
        }

        @Test
        @DisplayName("URL кнопки содержит ?startapp=telegram_channel1")
        void buttonUrlContainsStartapp() throws Exception {
            bot.consume(buildUpdate(CHAT_ID, "/start telegram_channel1"));

            ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
            verify(telegramClient).execute(captor.capture());

            String webAppUrl = extractWebAppUrl(captor.getValue());
            assertThat(webAppUrl).isEqualTo(APP_URL + "?startapp=telegram_channel1");
        }

        @Test
        @DisplayName("Реферальный код tiktok_video1 передаётся корректно")
        void tiktokCode_passedCorrectly() throws Exception {
            bot.consume(buildUpdate(CHAT_ID, "/start tiktok_video1"));

            verify(referralService).recordBotEntry(CHAT_ID, "tiktok_video1");

            ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
            verify(telegramClient).execute(captor.capture());
            assertThat(extractWebAppUrl(captor.getValue()))
                    .isEqualTo(APP_URL + "?startapp=tiktok_video1");
        }

        @Test
        @DisplayName("chatId из update передаётся в recordBotEntry (а не хардкод)")
        void correctChatIdPassedToService() throws Exception {
            long anotherChatId = 987654321L;
            bot.consume(buildUpdate(anotherChatId, "/start telegram_channel1"));

            verify(referralService).recordBotEntry(anotherChatId, "telegram_channel1");
        }
    }

    // ── Не-start сообщения ────────────────────────────────────────────────────

    @Test
    @DisplayName("Произвольный текст (не /start) — ничего не происходит")
    void nonStartMessage_noInteractions() throws Exception {
        bot.consume(buildUpdate(CHAT_ID, "Привет!"));
        verifyNoInteractions(referralService);
        verifyNoInteractions(telegramClient);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Update buildUpdate(long chatId, String text) {
        Message message = mock(Message.class);
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn(text);
        when(message.getChatId()).thenReturn(chatId);

        Update update = mock(Update.class);
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        return update;
    }

    private String extractWebAppUrl(SendMessage sendMessage) {
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) sendMessage.getReplyMarkup();
        List<InlineKeyboardButton> row = markup.getKeyboard().get(0);
        return row.get(0).getWebApp().getUrl();
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
