package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.sapa.gadalka_backend.domain.UserProfile;
import ru.sapa.gadalka_backend.domain.type.NotificationTime;
import ru.sapa.gadalka_backend.repository.UserProfileRepository;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Планировщик ежедневных уведомлений в Telegram.
 * Активен только когда telegram.bot.enabled=true (т.е. в prod-среде).
 *
 * <p>Каждый пользователь получает случайно выбранное сообщение из пула.
 * В тексте доступен плейсхолдер {name}, который заменяется на firstName пользователя.
 * Если имя не задано — плейсхолдер убирается.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.bot.enabled", havingValue = "true")
public class NotificationSchedulerService {

    private final TelegramClient telegramClient;
    private final UserProfileRepository userProfileRepository;

    @Value("${telegram.bot.app-url}")
    private String appUrl;

    /** Плейсхолдер имени — заменяется на firstName пользователя */
    private static final String NAME_PLACEHOLDER = "{name}";

    // ── Пулы сообщений ────────────────────────────────────────────────────────

    /**
     * Утренние сообщения. Из этого списка случайно выбирается одно при каждой рассылке.
     * {name} заменяется на firstName пользователя (или пустую строку, если имени нет).
     */
    private static final List<String> MORNING_MESSAGES = List.of(
            "🌅 *Доброе утро, {name}!*\n\nКарты уже готовы рассказать, что этот день припас для тебя. Открой расклад и начни день осознанно 🔮",
            "✨ *{name}, утро — лучшее время для карт!*\n\nПока суета не захлестнула день, задай вопрос и получи ясный ответ 🌿",
            "🌙 *Карты думали о тебе всю ночь, {name}*\n\nСегодня у тебя особенный день - загляни в расклад и узнай, что готовит судьба 🌟",
            "🔮 *С добрым утром, {name}!*\n\nТри карты могут изменить всё. Один вопрос - один ответ - один шаг вперёд. Попробуй прямо сейчас 💫",
            "🌸 *Новый день, новые возможности, {name}*\n\nКарты Таро помогут выбрать верный путь. Твой ежедневный расклад ждёт тебя ✨",
            "🌞 *{name}, удача любит тех, кто готов*\n\nНачни утро с карты дня — она подскажет, на что обратить внимание сегодня 🌙",
            "💎 *Доброе утро! Карты ждут тебя, {name}*\n\nПосмотри, что вселенная хочет сказать тебе прямо сейчас — открой расклад 🔮"
    );

    /**
     * Вечерние сообщения.
     */
    private static final List<String> EVENING_MESSAGES = List.of(
            "🌙 *Вечер — время для тайн, {name}*\n\nРасскажи картам, что у тебя на уме, и получи честный ответ перед сном 🔮",
            "✨ *{name}, как прошёл твой день?*\n\nКарты готовы осмыслить его вместе с тобой и подсказать, что ждёт завтра 🌟",
            "🌛 *Вечер подходит для глубоких вопросов, {name}*\n\nОткрой расклад — пусть карты озарят то, что скрыто за суетой дня 💫",
            "🕯️ *Тихий вечер, честные карты, {name}*\n\nЗадай вопрос, который беспокоит тебя, и узнай ответ прямо сейчас ✨",
            "🌌 *Звёзды и карты работают ночью, {name}*\n\nСамое время заглянуть в будущее — твой вечерний расклад готов 🔮",
            "🌙 *{name}, не ложись спать с вопросами*\n\nКарты дадут ответ и освободят разум от тревог. Попробуй прямо сейчас 🌿",
            "🌠 *Вечер — портал в понимание, {name}*\n\nТаро поможет увидеть день с другой стороны. Один расклад — и всё станет яснее 💎"
    );

    // ── Расписание ────────────────────────────────────────────────────────────

    /**
     * Утренняя рассылка — 9:00 по Москве (UTC+3 = 06:00 UTC).
     * cron формат: секунды минуты часы день месяц день_недели
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Europe/Moscow")
    public void sendMorningNotifications() {
        log.info("Запуск утренней рассылки уведомлений");
        sendNotifications(NotificationTime.MORNING, MORNING_MESSAGES);
    }

    /**
     * Вечерняя рассылка — 20:00 по Москве.
     */
    @Scheduled(cron = "0 0 20 * * *", zone = "Europe/Moscow")
    public void sendEveningNotifications() {
        log.info("Запуск вечерней рассылки уведомлений");
        sendNotifications(NotificationTime.EVENING, EVENING_MESSAGES);
    }

    // ── Логика отправки ───────────────────────────────────────────────────────

    private void sendNotifications(NotificationTime notificationTime, List<String> messagePool) {
        List<UserProfile> profiles = userProfileRepository.findByNotificationTime(notificationTime);
        log.info("Найдено {} пользователей для рассылки ({})", profiles.size(), notificationTime);

        int successCount = 0;
        int errorCount = 0;

        for (UserProfile profile : profiles) {
            Long telegramId = profile.getUser().getTelegramId();
            // Персонализация: заменяем {name} на имя пользователя (или пустую строку)
            String firstName = StringUtils.defaultString(profile.getUser().getFirstName(), "");
            String messageText = pickRandom(messagePool)
                    .replace(NAME_PLACEHOLDER, firstName)
                    .trim();

            try {
                sendNotificationMessage(telegramId, messageText);
                successCount++;
            } catch (TelegramApiException e) {
                log.warn("Не удалось отправить уведомление пользователю telegramId={}: {}", telegramId, e.getMessage());
                errorCount++;
            } catch (Exception e) {
                log.error("Неожиданная ошибка при отправке уведомления telegramId={}: {}", telegramId, e.getMessage(), e);
                errorCount++;
            }
        }

        log.info("Рассылка {} завершена: успешно={}, ошибок={}", notificationTime, successCount, errorCount);
    }

    /** Выбирает случайный элемент из пула сообщений */
    private String pickRandom(List<String> pool) {
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private void sendNotificationMessage(Long chatId, String text) throws TelegramApiException {
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text("🔮 Открыть Гадалку")
                .webApp(new WebAppInfo(appUrl))
                .build();

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboard(List.of(new InlineKeyboardRow(button)))
                .build();

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(keyboard)
                .build();

        telegramClient.execute(message);
    }
}
