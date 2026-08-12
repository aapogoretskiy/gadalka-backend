package ru.sapa.gadalka_backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.repository.UserRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Достижимость пользователя ботом ({@code User.notificationsAllowed}) — единственное
 * место, где этот флаг выставляется по инициативе приложения, а не по факту рассылки.
 *
 * <p>Зачем это нужно. Раньше флаг ставил только бот, когда ему приходило входящее
 * сообщение — включая служебное {@code write_access_allowed} после
 * {@code WebApp.requestWriteAccess()}. Но Telegram присылает это служебное сообщение
 * только когда разрешение реально МЕНЯЕТСЯ. Если пользователь когда-то жал /start,
 * право писать у бота уже есть: {@code requestWriteAccess()} возвращает true сразу,
 * служебного сообщения нет, флаг в БД навсегда остаётся false. Из-за этого баннер
 * «Разреши боту писать тебе» показывался снова после каждой перезагрузки, а сам
 * пользователь числился недостижимым в сегментах админки.
 *
 * <p>Плюс отдельная дыра: бот не мог отметить пользователя, которого ещё нет в БД
 * (типичный путь — /start в боте до первого открытия мини-аппа).
 *
 * <p>Поэтому здесь два метода, оба проверяют достижимость фактом обращения к Telegram,
 * а не доверием к клиенту:
 * <ul>
 *   <li>{@link #confirmWriteAccess(User)} — после нажатия «Разрешить» в мини-аппе:
 *       шлём приветственное сообщение, и если оно доставилось — бот точно может писать.</li>
 *   <li>{@link #pingReachabilityAsync(Long)} — при авторизации, если флаг ещё false:
 *       невидимый {@code sendChatAction}, который требует того же права, но не оставляет
 *       следа в чате.</li>
 * </ul>
 *
 * <p>{@code TelegramClient} берём через {@link ObjectProvider}, а не внедряем напрямую:
 * его бин существует только при {@code telegram.bot.enabled=true} (в CI бот выключен),
 * а этот сервис тянет за собой MeController, который поднимается всегда.
 */
@Slf4j
@Service
public class NotificationAccessService {

    /** Не чаще одной тихой проверки на пользователя в сутки. */
    private static final Duration PING_COOLDOWN = Duration.ofHours(24);

    /**
     * Предохранитель от роста карты троттлинга. Состояние не ценное: при очистке
     * максимум сделаем один лишний пинг, поэтому проще обнулить целиком, чем
     * тащить в проект отдельный кэш с вытеснением.
     */
    private static final int PING_CACHE_LIMIT = 10_000;

    private static final String WELCOME_TEXT = """
            🔮 *Спасибо! Теперь я на связи*

            Буду присылать твой расклад дня и важные знаки. Загляни в Liora — карты уже ждут ✨""";

    private final ObjectProvider<TelegramClient> telegramClientProvider;
    private final UserRepository userRepository;
    private final String appUrl;

    /** telegramId -> момент последней тихой проверки. */
    private final Map<Long, Instant> lastPingAt = new ConcurrentHashMap<>();

    public NotificationAccessService(ObjectProvider<TelegramClient> telegramClientProvider,
                                     UserRepository userRepository,
                                     @Value("${telegram.bot.app-url}") String appUrl) {
        this.telegramClientProvider = telegramClientProvider;
        this.userRepository = userRepository;
        this.appUrl = appUrl;
    }

    /**
     * Пользователь нажал «Разрешить» в мини-аппе. Проверяем доступ реальной отправкой
     * приветствия: доставилось — значит бот может писать, ставим флаг.
     *
     * @return true, если бот действительно может писать этому пользователю
     */
    @Transactional
    public boolean confirmWriteAccess(User user) {
        // Уже отмечен — второе приветствие не шлём (пользователь может нажать кнопку
        // несколько раз, а сообщение должно быть одно).
        if (user.isNotificationsAllowed()) {
            return true;
        }

        TelegramClient client = telegramClientProvider.getIfAvailable();
        if (client == null) {
            log.warn("Бот выключен (telegram.bot.enabled=false) — не можем подтвердить доступ для userId={}", user.getId());
            return false;
        }

        try {
            client.execute(welcomeMessage(user.getTelegramId()));
        } catch (Exception e) {
            // Типичное: "bot was blocked by the user" / "chat not found" — пользователь
            // разрешил запись в мини-аппе, но диалога с ботом у него нет.
            log.warn("Не удалось отправить приветствие telegramId={}: {}", user.getTelegramId(), e.getMessage());
            return false;
        }

        markAllowed(user, "нажал «Разрешить» в мини-аппе");
        return true;
    }

    /**
     * Тихая проверка при авторизации для тех, у кого флаг ещё false: вдруг бот
     * на самом деле может писать (например, пользователь жал /start до регистрации).
     * {@code sendChatAction} требует того же права, что и обычное сообщение, но
     * не оставляет следа в чате.
     *
     * <p>Вызывать только из другого бина — {@code @Async} работает через прокси,
     * при вызове изнутри своего же класса метод выполнится синхронно.
     */
    @Async
    public void pingReachabilityAsync(Long userId) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || user.isNotificationsAllowed()) {
                return;
            }
            if (!shouldPing(user.getTelegramId())) {
                return;
            }

            TelegramClient client = telegramClientProvider.getIfAvailable();
            if (client == null) {
                return;
            }

            client.execute(SendChatAction.builder()
                    .chatId(String.valueOf(user.getTelegramId()))
                    .action("typing")
                    .build());

            markAllowed(user, "тихая проверка при авторизации");
        } catch (Exception e) {
            // Недостижим — это нормальный исход проверки, а не сбой: флаг и так false.
            log.debug("Тихая проверка достижимости не прошла для userId={}: {}", userId, e.getMessage());
        }
    }

    private void markAllowed(User user, String reason) {
        userRepository.findById(user.getId()).ifPresent(fresh -> {
            if (fresh.isNotificationsAllowed()) {
                return;
            }
            fresh.setNotificationsAllowed(true);
            userRepository.save(fresh);
            log.info("notificationsAllowed=true для telegramId={} ({})", fresh.getTelegramId(), reason);
        });
        // Экземпляр из запроса тоже обновляем — иначе ответ этого же запроса
        // отдал бы устаревшее значение.
        user.setNotificationsAllowed(true);
    }

    private boolean shouldPing(Long telegramId) {
        if (lastPingAt.size() > PING_CACHE_LIMIT) {
            lastPingAt.clear();
        }
        Instant previous = lastPingAt.get(telegramId);
        if (previous != null && Duration.between(previous, Instant.now()).compareTo(PING_COOLDOWN) < 0) {
            return false;
        }
        lastPingAt.put(telegramId, Instant.now());
        return true;
    }

    private SendMessage welcomeMessage(Long chatId) {
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text("🔮 Открыть Liora")
                .webApp(new WebAppInfo(appUrl))
                .build();

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboard(List.of(new InlineKeyboardRow(button)))
                .build();

        return SendMessage.builder()
                .chatId(chatId)
                .text(WELCOME_TEXT)
                .parseMode("Markdown")
                .replyMarkup(keyboard)
                .build();
    }
}
