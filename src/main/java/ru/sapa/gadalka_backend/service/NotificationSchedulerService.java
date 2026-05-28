package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

/**
 * Планировщик уведомлений в Telegram.
 * Активен только когда telegram.bot.enabled=true (т.е. в prod-среде).
 * В dev-среде бот отключён — уведомления не уходят.
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

    /**
     * Утренняя рассылка — 9:00 по Москве (UTC+3 = 06:00 UTC).
     * cron формат: секунды минуты часы день месяц день_недели
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Europe/Moscow")
    public void sendMorningNotifications() {
        log.info("Запуск утренней рассылки уведомлений");
        sendNotifications(NotificationTime.MORNING);
    }

    /**
     * Вечерняя рассылка — 20:00 по Москве.
     */
    @Scheduled(cron = "0 0 20 * * *", zone = "Europe/Moscow")
    public void sendEveningNotifications() {
        log.info("Запуск вечерней рассылки уведомлений");
        sendNotifications(NotificationTime.EVENING);
    }

    private void sendNotifications(NotificationTime notificationTime) {
        List<UserProfile> profiles = userProfileRepository.findByNotificationTime(notificationTime);
        log.info("Найдено {} пользователей для рассылки ({})", profiles.size(), notificationTime);

        int successCount = 0;
        int errorCount = 0;

        for (UserProfile profile : profiles) {
            Long telegramId = profile.getUser().getTelegramId();
            try {
                sendNotificationMessage(telegramId);
                successCount++;
            } catch (TelegramApiException e) {
                log.warn("Не удалось отправить уведомление пользователю telegramId={}: {}", telegramId, e.getMessage());
                errorCount++;
            } catch (Exception e) {
                log.error("Неожиданная ошибка при отправке уведомления пользователю telegramId={}: {}", telegramId, e.getMessage(), e);
                errorCount++;
            }
        }

        log.info("Рассылка {} завершена: успешно={}, ошибок={}",
                notificationTime, successCount, errorCount);
    }

    private void sendNotificationMessage(Long chatId) throws TelegramApiException {
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text("🔮 Открыть Гадалку")
                .webApp(new WebAppInfo(appUrl))
                .build();

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboard(List.of(new InlineKeyboardRow(button)))
                .build();

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("""
                        🌙 *Карты снова думают о тебе*
                      
                        Расскажи что у тебя сейчас на уме?""")
                .parseMode("Markdown")
                .replyMarkup(keyboard)
                .build();

        telegramClient.execute(message);
    }
}
