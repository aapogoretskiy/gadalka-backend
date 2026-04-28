package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.domain.ReferralEvent;
import ru.sapa.gadalka_backend.domain.User;

import ru.sapa.gadalka_backend.domain.type.ReferralEventType;
import ru.sapa.gadalka_backend.repository.ReferralEventRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;

/**
 * Сервис реферальных ссылок.
 *
 * <p>Два типа событий:
 * <ol>
 *   <li><b>BOT_ENTRY</b> — бот получил команду {@code /start CODE}: пользователь кликнул по ссылке.
 *       Записывается немедленно, {@code userId} и {@code isNewUser} ещё неизвестны.</li>
 *   <li><b>APP_OPEN</b> — Mini App открылся с {@code start_param=CODE} в initData:
 *       пользователь фактически вошёл в приложение.
 *       Записывается при авторизации, {@code userId} и {@code isNewUser} уже известны.</li>
 * </ol>
 *
 * <p>Дополнительно: при первой регистрации нового пользователя поле
 * {@code users.referral_source} проставляется один раз и больше не меняется.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReferralService {

    private final ReferralEventRepository referralEventRepository;
    private final UserRepository userRepository;

    /**
     * Записывает факт перехода по deep-link через бот ({@code /start CODE}).
     *
     * @param telegramId Telegram ID пользователя
     * @param code       реферальный код (например, "telegram_channel1")
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
     * <p>Если пользователь новый — проставляет {@code users.referral_source} (один раз).
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
    }
}
