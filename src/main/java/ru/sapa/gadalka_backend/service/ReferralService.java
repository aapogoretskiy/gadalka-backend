package ru.sapa.gadalka_backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.bot.GadalkaTelegramBot;
import ru.sapa.gadalka_backend.domain.ReferralEvent;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.type.CreditTransactionReason;
import ru.sapa.gadalka_backend.domain.type.ReferralEventType;
import ru.sapa.gadalka_backend.repository.ReferralEventRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;

import java.util.Optional;

/**
 * Сервис реферальных ссылок.
 *
 * <p>Два вида рефералов:
 * <ol>
 *   <li><b>Маркетинговые</b> — коды типа "telegram_channel1", "tiktok_video1".
 *       Отслеживают откуда пришёл пользователь.</li>
 *   <li><b>Пользовательские (user-to-user)</b> — коды вида {@code ref_<telegramId>}.
 *       Когда новый пользователь регистрируется по такой ссылке, реферер получает
 *       {@value REFERRAL_REWARD_CREDITS} знаков и уведомление в бот.</li>
 * </ol>
 *
 * <p>Три типа событий:
 * <ul>
 *   <li><b>BOT_ENTRY</b>    — бот получил /start CODE</li>
 *   <li><b>APP_OPEN</b>     — Mini App открылся с start_param=CODE в initData</li>
 *   <li><b>USER_REFERRAL</b>— новый пользователь зарегистрировался по ссылке другого юзера</li>
 * </ul>
 */
@Slf4j
@Service
public class ReferralService {

    /** Количество знаков, начисляемых рефереру за каждого приглашённого пользователя. */
    public static final int REFERRAL_REWARD_CREDITS = 3;

    /** Префикс пользовательских реферальных кодов. */
    private static final String USER_REF_PREFIX = "ref_";

    private final ReferralEventRepository referralEventRepository;
    private final UserRepository userRepository;
    private final FortuneCreditService fortuneCreditService;

    /**
     * Бот инжектируется лениво (@Lazy), чтобы разорвать циклическую зависимость:
     * GadalkaTelegramBot → ReferralService → GadalkaTelegramBot.
     * Spring создаст прокси при старте и подставит реальный бин при первом обращении.
     */
    private final GadalkaTelegramBot telegramBot;

    public ReferralService(ReferralEventRepository referralEventRepository,
                           UserRepository userRepository,
                           FortuneCreditService fortuneCreditService,
                           @Lazy GadalkaTelegramBot telegramBot) {
        this.referralEventRepository = referralEventRepository;
        this.userRepository = userRepository;
        this.fortuneCreditService = fortuneCreditService;
        this.telegramBot = telegramBot;
    }

    /**
     * Записывает факт перехода по deep-link через бот ({@code /start CODE}).
     *
     * @param telegramId Telegram ID пользователя
     * @param code       реферальный код (например, "telegram_channel1" или "ref_123456789")
     */
    @Transactional
    public void recordBotEntry(long telegramId, String code) {
        if (code == null || code.isBlank()) return;

        ReferralEvent event = ReferralEvent.builder()
                .referralCode(code)
                .telegramId(telegramId)
                .eventType(ReferralEventType.BOT_ENTRY)
                .build();

        referralEventRepository.save(event);
        log.info("Реферальное событие BOT_ENTRY: telegramId={}, code={}", telegramId, code);
    }

    /**
     * Записывает факт открытия Mini App с реферальным параметром.
     * Вызывается из {@code TelegramAuthService} при авторизации.
     *
     * <p>Если код пользовательский (начинается с {@code ref_}) и пользователь новый —
     * дополнительно начисляет знаки рефереру и отправляет ему уведомление в бот.
     *
     * @param telegramId Telegram ID пользователя
     * @param user       авторизованный пользователь (уже сохранён в БД)
     * @param isNewUser  был ли пользователь создан в рамках этого вызова
     * @param code       реферальный код из {@code start_param}
     */
    @Transactional
    public void recordAppOpen(long telegramId, User user, boolean isNewUser, String code) {
        if (code == null || code.isBlank()) return;

        ReferralEvent event = ReferralEvent.builder()
                .referralCode(code)
                .telegramId(telegramId)
                .userId(user.getId())
                .isNewUser(isNewUser)
                .eventType(ReferralEventType.APP_OPEN)
                .build();

        referralEventRepository.save(event);
        log.info("Реферальное событие APP_OPEN: telegramId={}, userId={}, isNewUser={}, code={}",
                telegramId, user.getId(), isNewUser, code);

        // Сохраняем источник регистрации только один раз — при первом визите нового пользователя
        if (isNewUser && user.getReferralSource() == null) {
            user.setReferralSource(code);
            userRepository.save(user);
            log.info("Источник регистрации сохранён: userId={}, referralSource={}", user.getId(), code);
        }

        // Если это пользовательская реферальная ссылка и пользователь новый — награждаем реферера
        if (isNewUser && isUserReferralCode(code)) {
            processUserReferral(user, code);
        }
    }

    /**
     * Пробует начислить реферальное вознаграждение новому пользователю, который открыл
     * приложение без {@code start_param} (например, через кнопку меню бота).
     *
     * <p>Ищет последний {@code BOT_ENTRY} по данному {@code telegramId} — если нашли,
     * значит пользователь кликал по реф-ссылке до открытия приложения. Делегируем
     * в {@link #recordAppOpen} с найденным кодом, чтобы начислить награду рефереру.
     *
     * <p>Вызывается из {@code TelegramAuthService} только для новых пользователей.
     *
     * @param telegramId Telegram ID нового пользователя
     * @param user       только что созданный пользователь
     */
    @Transactional
    public void tryRecordFromBotEntry(long telegramId, User user) {
        referralEventRepository
                .findTopByTelegramIdAndEventTypeOrderByCreatedAtDesc(telegramId, ReferralEventType.BOT_ENTRY)
                .ifPresent(botEntry -> {
                    log.info("Найден BOT_ENTRY для нового пользователя без start_param: " +
                            "telegramId={}, code={}", telegramId, botEntry.getReferralCode());
                    recordAppOpen(telegramId, user, true, botEntry.getReferralCode());
                });
    }

    /**
     * Возвращает реферальный код пользователя (используется для генерации ссылки на фронте).
     * Формат: {@code ref_<telegramId>}
     */
    public String buildReferralCode(Long telegramId) {
        return USER_REF_PREFIX + telegramId;
    }

    // ── Внутренние методы ────────────────────────────────────────────────────

    private boolean isUserReferralCode(String code) {
        return code.startsWith(USER_REF_PREFIX);
    }

    /**
     * Обрабатывает user-to-user реферал:
     * находит реферера, начисляет знаки, отправляет уведомление, сохраняет событие.
     */
    private void processUserReferral(User newUser, String code) {
        // Парсим telegramId реферера из кода вида "ref_123456789"
        Long referrerTelegramId;
        try {
            referrerTelegramId = Long.parseLong(code.substring(USER_REF_PREFIX.length()));
        } catch (NumberFormatException e) {
            log.warn("Некорректный пользовательский реферальный код: code={}", code);
            return;
        }

        if (referrerTelegramId.equals(newUser.getTelegramId())) {
            log.warn("Попытка само-реферала: telegramId={}", referrerTelegramId);
            return;
        }

        Optional<User> referrerOpt = userRepository.findByTelegramId(referrerTelegramId);
        if (referrerOpt.isEmpty()) {
            log.warn("Реферер не найден: referrerTelegramId={}", referrerTelegramId);
            return;
        }
        User referrer = referrerOpt.get();

        // Начисляем знаки рефереру
        fortuneCreditService.grantCredits(referrer.getId(), REFERRAL_REWARD_CREDITS, CreditTransactionReason.REFERRAL_REWARD, null);

        // Отправляем благодарственное уведомление в бот
        String newUserName = newUser.getFirstName() != null ? newUser.getFirstName() : "Новый пользователь";
        telegramBot.sendReferralRewardNotification(referrer.getTelegramId(), newUserName, REFERRAL_REWARD_CREDITS);

        // Сохраняем событие USER_REFERRAL для отчётности
        ReferralEvent userReferralEvent = ReferralEvent.builder()
                .referralCode(code)
                .telegramId(newUser.getTelegramId())
                .userId(newUser.getId())
                .isNewUser(true)
                .eventType(ReferralEventType.USER_REFERRAL)
                .referrerUserId(referrer.getId())
                .build();
        referralEventRepository.save(userReferralEvent);

        log.info("Реферальное вознаграждение начислено: referrerId={}, newUserId={}, credits={}", referrer.getId(), newUser.getId(), REFERRAL_REWARD_CREDITS);
    }
}
