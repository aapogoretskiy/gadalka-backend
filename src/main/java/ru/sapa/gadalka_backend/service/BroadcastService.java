package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.bot.GadalkaTelegramBot;
import ru.sapa.gadalka_backend.constant.SystemConfigConstants;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.type.CreditTransactionReason;
import ru.sapa.gadalka_backend.repository.SystemConfigRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;

import java.util.List;

/**
 * Сервис массовой рассылки сообщений через Telegram-бот.
 *
 * <p>Все методы помечены {@code @Async} — рассылка запускается в фоновом потоке
 * и не блокирует HTTP-запрос администратора.
 *
 * <p>Персонализация: если в тексте встречается плейсхолдер {@code {name}} и фича-тогл
 * {@code BROADCAST_PERSONALIZATION_ENABLED} равен "true" в system_config,
 * плейсхолдер заменяется на firstName пользователя (или на пустую строку, если имени нет).
 *
 * <p>Между отправками добавляется задержка 100 мс:
 * Telegram разрешает ~30 сообщений/сек одному боту, нам этого более чем достаточно.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BroadcastService {

    /** Задержка между сообщениями (мс). Telegram лимит — ~30 msg/сек. */
    private static final long SEND_DELAY_MS = 100;

    /** Размер батча при выборке всех пользователей из БД. */
    private static final int BATCH_SIZE = 200;

    /** Плейсхолдер имени пользователя в тексте рассылки */
    private static final String NAME_PLACEHOLDER = "{name}";

    private final UserRepository userRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final FortuneCreditService fortuneCreditService;
    private final GadalkaTelegramBot telegramBot;

    /**
     * Запускает рассылку в фоновом потоке.
     *
     * @param message    текст сообщения для отправки в Telegram
     * @param giftAmount количество знаков для начисления (null или 0 — не начислять)
     * @param userIds    список внутренних ID пользователей; если null или пусто — рассылка всем
     * @param photoUrl   URL изображения для отправки; если null или пусто — только текст
     */
    @Async
    public void broadcast(String message, Integer giftAmount, List<Long> userIds, String photoUrl) {
        boolean giftEnabled = giftAmount != null && giftAmount > 0;
        boolean toAll = userIds == null || userIds.isEmpty();
        boolean personalized = isPersonalizationEnabled();
        boolean hasPhoto = photoUrl != null && !photoUrl.isBlank();

        log.info("Рассылка запущена: toAll={}, recipients={}, giftAmount={}, personalized={}, hasPhoto={}, message.length={}",
                toAll,
                toAll ? "all" : userIds.size(),
                giftAmount,
                personalized,
                hasPhoto,
                message.length());

        if (toAll) {
            broadcastToAll(message, giftEnabled, giftAmount, personalized, photoUrl);
        } else {
            broadcastToSelected(message, giftEnabled, giftAmount, userIds, personalized, photoUrl);
        }
    }

    /**
     * Рассылка всем пользователям батчами, чтобы не грузить память при большой базе.
     */
    private void broadcastToAll(String message,
                                boolean giftEnabled,
                                Integer giftAmount,
                                boolean personalized,
                                String photoUrl) {
        int page = 0;
        int sent = 0;
        int failed = 0;

        while (true) {
            List<User> batch = userRepository.findAll(PageRequest.of(page, BATCH_SIZE)).getContent();
            if (batch.isEmpty()) break;

            for (User user : batch) {
                boolean ok = sendToUser(user, message, giftEnabled, giftAmount, personalized, photoUrl);
                if (ok) sent++; else failed++;
                sleep();
            }
            page++;
        }

        log.info("Рассылка завершена (всем): отправлено={}, ошибок={}", sent, failed);
    }

    /**
     * Рассылка выбранным пользователям по внутренним ID.
     */
    private void broadcastToSelected(String message,
                                     boolean giftEnabled,
                                     Integer giftAmount,
                                     List<Long> userIds,
                                     boolean personalized,
                                     String photoUrl) {
        List<User> users = userRepository.findAllById(userIds);
        int sent = 0;
        int failed = 0;

        for (User user : users) {
            boolean ok = sendToUser(user, message, giftEnabled, giftAmount, personalized, photoUrl);
            if (ok) sent++; else failed++;
            sleep();
        }

        log.info("Рассылка завершена (выбранным): запрошено={}, отправлено={}, ошибок={}", userIds.size(), sent, failed);
    }

    /**
     * Отправляет сообщение одному пользователю.
     * Если включена персонализация — заменяет {name} на firstName.
     * Если задан photoUrl — использует sendPhotoBroadcastMessage, иначе sendBroadcastMessage.
     *
     * @return true если сообщение отправлено успешно
     */
    private boolean sendToUser(User user,
                               String message,
                               boolean giftEnabled,
                               Integer giftAmount,
                               boolean personalized,
                               String photoUrl) {
        try {
            if (giftEnabled) {
                fortuneCreditService.grantCredits(user.getId(),
                        giftAmount,
                        CreditTransactionReason.ADMIN_BROADCAST,
                        null);
            }

            String personalizedMessage = personalized
                    ? message.replace(NAME_PLACEHOLDER, StringUtils.defaultString(user.getFirstName(), StringUtils.EMPTY))
                    : message;

            Integer gift = giftEnabled ? giftAmount : null;
            if (photoUrl != null && !photoUrl.isBlank()) {
                telegramBot.sendPhotoBroadcastMessage(user.getTelegramId(), photoUrl, personalizedMessage, gift);
            } else {
                telegramBot.sendBroadcastMessage(user.getTelegramId(), personalizedMessage, gift);
            }
            return true;
        } catch (Exception e) {
            log.warn("Ошибка рассылки: userId={}, telegramId={}, error={}", user.getId(), user.getTelegramId(), e.getMessage());
            return false;
        }
    }

    /**
     * Читает значение фича-тогла персонализации из system_config.
     * Если запись отсутствует или значение != "true" — персонализация отключена.
     */
    private boolean isPersonalizationEnabled() {
        return systemConfigRepository.findByKey(SystemConfigConstants.BROADCAST_PERSONALIZATION_ENABLED)
                .map(c -> "true".equalsIgnoreCase(c.getValue()))
                .orElse(false);
    }

    private void sleep() {
        try {
            Thread.sleep(SEND_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Рассылка прервана");
        }
    }
}
