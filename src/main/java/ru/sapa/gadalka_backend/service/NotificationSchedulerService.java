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
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.UserProfile;
import ru.sapa.gadalka_backend.domain.type.NotificationTime;
import ru.sapa.gadalka_backend.repository.UserProfileRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;
import ru.sapa.gadalka_backend.service.notification.NotificationMessage;
import ru.sapa.gadalka_backend.service.notification.NotificationMessageCatalog;
import ru.sapa.gadalka_backend.service.notification.NotificationPlaceholderResolver;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Планировщик ежедневных уведомлений в Telegram.
 * Активен только когда telegram.bot.enabled=true (т.е. в prod-среде).
 *
 * <p>Что изменилось по сравнению с первой версией:
 * <ul>
 *   <li>тексты переехали в {@link NotificationMessageCatalog} и стали структурой:
 *       у каждого сообщения своя кнопка и свой экран назначения, а не общая
 *       «Открыть Гадалку» на главную;</li>
 *   <li>цены не захардкожены — подставляются из БД
 *       ({@link NotificationPlaceholderResolver});</li>
 *   <li>сообщение выбирается не случайно, а по детерминированной ротации:
 *       раньше {@code random} мог выдать одному человеку один и тот же текст
 *       несколько дней подряд.</li>
 * </ul>
 *
 * <p>Пропорция продающих и атмосферных сообщений — см. {@link #PROMO_EVERY_N_DAYS}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.bot.enabled", havingValue = "true")
public class NotificationSchedulerService {

    /** Часовой пояс, в котором считается «номер дня» для ротации — тот же, что у cron. */
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    /**
     * Каждое N-е сообщение для конкретного пользователя — продающее, остальные атмосферные.
     * Ежедневная продажа выжигает базу: люди перестают открывать сообщения и отписываются.
     */
    private static final int PROMO_EVERY_N_DAYS = 3;

    private final TelegramClient telegramClient;
    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;
    private final NotificationPlaceholderResolver placeholderResolver;

    @Value("${telegram.bot.app-url}")
    private String appUrl;

    // ── Расписание ────────────────────────────────────────────────────────────

    /**
     * Утренняя рассылка — 9:00 по Москве (UTC+3 = 06:00 UTC).
     * cron формат: секунды минуты часы день месяц день_недели
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Europe/Moscow")
    public void sendMorningNotifications() {
        log.info("Запуск утренней рассылки уведомлений");
        sendNotifications(NotificationTime.MORNING, NotificationMessage.Slot.MORNING);
    }

    /**
     * Вечерняя рассылка — 20:00 по Москве.
     */
    @Scheduled(cron = "0 0 20 * * *", zone = "Europe/Moscow")
    public void sendEveningNotifications() {
        log.info("Запуск вечерней рассылки уведомлений");
        sendNotifications(NotificationTime.EVENING, NotificationMessage.Slot.EVENING);
    }

    // ── Логика отправки ───────────────────────────────────────────────────────

    private void sendNotifications(NotificationTime notificationTime, NotificationMessage.Slot slot) {
        List<UserProfile> profiles = userProfileRepository.findByNotificationTime(notificationTime);
        log.info("Найдено {} пользователей для рассылки ({})", profiles.size(), notificationTime);
        if (profiles.isEmpty()) {
            return;
        }

        // Снимок цен собираем один раз на всю рассылку, а не на каждого получателя
        Map<String, String> placeholders = placeholderResolver.snapshot();
        List<NotificationMessage> promoPool = NotificationMessageCatalog.forSlot(
                NotificationMessageCatalog.PROMO, slot);
        List<NotificationMessage> ambientPool = NotificationMessageCatalog.forSlot(
                NotificationMessageCatalog.AMBIENT, slot);

        long dayNumber = LocalDate.now(MOSCOW).toEpochDay();

        int successCount = 0;
        int errorCount = 0;
        int skippedCount = 0;
        int promoCount = 0;

        for (UserProfile profile : profiles) {
            User user = profile.getUser();
            Long telegramId = user.getTelegramId();
            long rotationKey = user.getId() + dayNumber;

            boolean promoTurn = Math.floorMod(rotationKey, PROMO_EVERY_N_DAYS) == 0;
            // Счётчики пулов считаем отдельно, иначе часть текстов не показалась бы
            // никогда: продающие идут раз в три дня, и если размер пула кратен трём,
            // «шаг 3» гоняет пользователя по одной и той же трети списка.
            long promoIndex = Math.floorDiv(rotationKey, PROMO_EVERY_N_DAYS);
            long ambientIndex = rotationKey - promoIndex;

            Optional<Prepared> prepared = promoTurn
                    ? pick(promoPool, promoIndex, placeholders, user)
                    : pick(ambientPool, ambientIndex, placeholders, user);
            if (prepared.isEmpty() && promoTurn) {
                // Все продающие тексты этого слота оказались неотправляемыми
                // (например, подписки выключены) — уходим в атмосферный пул
                prepared = pick(ambientPool, ambientIndex, placeholders, user);
            }
            if (prepared.isEmpty()) {
                skippedCount++;
                continue;
            }
            if (promoTurn) {
                promoCount++;
            }

            try {
                send(telegramId, prepared.get());
                successCount++;
                markReachable(user, true);
            } catch (TelegramApiException e) {
                log.warn("Не удалось отправить уведомление пользователю telegramId={}: {}", telegramId, e.getMessage());
                errorCount++;
                if (isPermanentDeliveryFailure(e.getMessage())) {
                    markReachable(user, false);
                }
            } catch (Exception e) {
                log.error("Неожиданная ошибка при отправке уведомления telegramId={}: {}", telegramId, e.getMessage(), e);
                errorCount++;
            }
        }

        log.info("Рассылка {} завершена: успешно={} (из них продающих={}), ошибок={}, пропущено={}",
                notificationTime, successCount, promoCount, errorCount, skippedCount);
    }

    /**
     * Выбирает сообщение из пула для конкретного пользователя.
     *
     * <p>Вместо случайного выбора — детерминированная ротация: индекс считается
     * от id пользователя и номера дня, поэтому каждый идёт по пулу своим циклом,
     * внутри цикла текст не повторяется, и хранить историю отправок не нужно.
     *
     * <p>Если у выбранного сообщения не раскрылись плейсхолдеры (цену не удалось
     * получить из БД), берётся следующее по кругу — но не более чем размер пула,
     * чтобы не зациклиться.
     */
    private Optional<Prepared> pick(List<NotificationMessage> pool, long poolIndex,
                                    Map<String, String> placeholders, User user) {
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        int start = (int) Math.floorMod(poolIndex, pool.size());
        for (int offset = 0; offset < pool.size(); offset++) {
            NotificationMessage candidate = pool.get((start + offset) % pool.size());
            Optional<Prepared> prepared = prepare(candidate, placeholders, user);
            if (prepared.isPresent()) {
                return prepared;
            }
        }
        log.warn("Ни одно сообщение пула не удалось подготовить к отправке (размер пула={})", pool.size());
        return Optional.empty();
    }

    /** Раскрывает цены и имя в тексте и в подписи кнопки, собирает ссылку кнопки. */
    private Optional<Prepared> prepare(NotificationMessage message, Map<String, String> placeholders, User user) {
        Optional<String> text = NotificationPlaceholderResolver.apply(message.text(), placeholders);
        Optional<String> button = NotificationPlaceholderResolver.apply(message.buttonText(), placeholders);
        if (text.isEmpty() || button.isEmpty()) {
            return Optional.empty();
        }

        String firstName = StringUtils.defaultString(user.getFirstName(), "");
        String personalizedText = text.get()
                .replace(NotificationPlaceholderResolver.NAME_PLACEHOLDER, firstName)
                .trim();

        return Optional.of(new Prepared(
                personalizedText,
                button.get(),
                message.target().buildUrl(appUrl)));
    }

    private void send(Long chatId, Prepared prepared) throws TelegramApiException {
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text(prepared.buttonText())
                .webApp(new WebAppInfo(prepared.url()))
                .build();

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboard(List.of(new InlineKeyboardRow(button)))
                .build();

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(prepared.text())
                .parseMode("Markdown")
                .replyMarkup(keyboard)
                .build();

        telegramClient.execute(message);
    }

    /**
     * Обновляет {@code User.notificationsAllowed} по факту реальной доставки —
     * см. миграцию V59 и аналогичный метод в {@code BroadcastService}.
     */
    private void markReachable(User user, boolean reachable) {
        if (user.isNotificationsAllowed() == reachable) return;
        user.setNotificationsAllowed(reachable);
        userRepository.save(user);
    }

    /** "chat not found" / "bot was blocked" — постоянные ошибки, остальное может быть временным сбоем. */
    private boolean isPermanentDeliveryFailure(String errorMessage) {
        if (errorMessage == null) return false;
        return errorMessage.contains("chat not found") || errorMessage.contains("bot was blocked");
    }

    /** Готовое к отправке сообщение: текст, подпись кнопки и её ссылка. */
    private record Prepared(String text, String buttonText, String url) {
    }
}
