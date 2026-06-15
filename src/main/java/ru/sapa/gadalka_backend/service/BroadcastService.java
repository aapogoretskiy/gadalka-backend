package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.bot.GadalkaTelegramBot;
import ru.sapa.gadalka_backend.configuration.AdminProperties;
import ru.sapa.gadalka_backend.constant.SystemConfigConstants;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.type.CreditTransactionReason;
import ru.sapa.gadalka_backend.repository.SystemConfigRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

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
    private final AdminProperties adminProperties;

    /**
     * Запускает рассылку в фоновом потоке.
     *
     * <p>Приоритет аудитории: userIds > onlyAdmins > все пользователи.
     *
     * <p>Если передан {@code photoBytes}: первому получателю файл загружается на серверы Telegram,
     * из ответа извлекается {@code file_id}, который переиспользуется для всех остальных.
     * Это позволяет загрузить файл один раз вместо N раз.
     *
     * @param message       текст сообщения для отправки в Telegram
     * @param giftAmount    количество знаков для начисления (null или 0 — не начислять)
     * @param userIds       список внутренних ID пользователей; если null или пусто — см. onlyAdmins
     * @param photoBytes    байты изображения (null — только текст)
     * @param photoFileName имя файла изображения (нужно Telegram для MIME-типа)
     * @param onlyAdmins    если true и userIds пусто — рассылка только администраторам из ADMIN_TELEGRAM_IDS
     */
    @Async
    public void broadcast(String message, Integer giftAmount, List<Long> userIds,
                          byte[] photoBytes, String photoFileName, boolean onlyAdmins) {
        boolean giftEnabled = giftAmount != null && giftAmount > 0;
        boolean toSelected = userIds != null && !userIds.isEmpty();
        boolean toAdmins = !toSelected && onlyAdmins;
        boolean toAll = !toSelected && !toAdmins;
        boolean personalized = isPersonalizationEnabled();
        boolean hasPhoto = photoBytes != null && photoBytes.length > 0;

        log.info("Рассылка запущена: toAll={}, toAdmins={}, toSelected={}, giftAmount={}, personalized={}, hasPhoto={}, message.length={}",
                toAll,
                toAdmins,
                toSelected ? userIds.size() : 0,
                giftAmount,
                personalized,
                hasPhoto,
                message.length());

        // Контейнер для file_id: первый пользователь загружает файл,
        // остальные используют закешированный идентификатор
        String[] cachedFileId = {null};

        if (toSelected) {
            broadcastToSelected(message, giftEnabled, giftAmount, userIds, personalized, photoBytes, photoFileName, cachedFileId);
        } else if (toAdmins) {
            broadcastToAdmins(message, giftEnabled, giftAmount, personalized, photoBytes, photoFileName, cachedFileId);
        } else {
            broadcastToAll(message, giftEnabled, giftAmount, personalized, photoBytes, photoFileName, cachedFileId);
        }
    }

    /**
     * Рассылка всем пользователям батчами, чтобы не грузить память при большой базе.
     */
    private void broadcastToAll(String message,
                                boolean giftEnabled,
                                Integer giftAmount,
                                boolean personalized,
                                byte[] photoBytes,
                                String photoFileName,
                                String[] cachedFileId) {
        int page = 0;
        int sent = 0;
        int failed = 0;

        while (true) {
            List<User> batch = userRepository.findAll(PageRequest.of(page, BATCH_SIZE)).getContent();
            if (batch.isEmpty()) break;

            for (User user : batch) {
                boolean ok = sendToUser(user, message, giftEnabled, giftAmount, personalized, photoBytes, photoFileName, cachedFileId);
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
                                     byte[] photoBytes,
                                     String photoFileName,
                                     String[] cachedFileId) {
        List<User> users = userRepository.findAllById(userIds);
        int sent = 0;
        int failed = 0;

        for (User user : users) {
            boolean ok = sendToUser(user, message, giftEnabled, giftAmount, personalized, photoBytes, photoFileName, cachedFileId);
            if (ok) sent++; else failed++;
            sleep();
        }

        log.info("Рассылка завершена (выбранным): запрошено={}, отправлено={}, ошибок={}", userIds.size(), sent, failed);
    }

    /**
     * Рассылка только администраторам по Telegram ID из {@code ADMIN_TELEGRAM_IDS}.
     * Ищет пользователей в БД — отправляем только тем, кто уже зарегистрирован в приложении.
     */
    private void broadcastToAdmins(String message,
                                   boolean giftEnabled,
                                   Integer giftAmount,
                                   boolean personalized,
                                   byte[] photoBytes,
                                   String photoFileName,
                                   String[] cachedFileId) {
        List<User> admins = Arrays.stream(adminProperties.getTelegramIds().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(id -> {
                    try {
                        return userRepository.findByTelegramId(Long.parseLong(id)).orElse(null);
                    } catch (NumberFormatException e) {
                        log.warn("Некорректный telegramId администратора в конфиге: '{}'", id);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();

        int sent = 0;
        int failed = 0;

        for (User admin : admins) {
            boolean ok = sendToUser(admin, message, giftEnabled, giftAmount, personalized, photoBytes, photoFileName, cachedFileId);
            if (ok) sent++; else failed++;
            sleep();
        }

        log.info("Рассылка завершена (администраторам): найдено={}, отправлено={}, ошибок={}", admins.size(), sent, failed);
    }

    /**
     * Отправляет сообщение одному пользователю.
     *
     * <p>Логика работы с фото:
     * <ul>
     *   <li>Если {@code photoBytes} != null и {@code cachedFileId[0]} == null — первая отправка:
     *       байты загружаются на серверы Telegram, полученный file_id сохраняется в {@code cachedFileId[0]}.</li>
     *   <li>Если {@code cachedFileId[0]} уже задан — переиспользуем file_id без повторной загрузки.</li>
     *   <li>Если {@code photoBytes} == null — отправляем только текст.</li>
     * </ul>
     *
     * @return true если сообщение отправлено успешно
     */
    private boolean sendToUser(User user,
                               String message,
                               boolean giftEnabled,
                               Integer giftAmount,
                               boolean personalized,
                               byte[] photoBytes,
                               String photoFileName,
                               String[] cachedFileId) {
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

            if (photoBytes != null && photoBytes.length > 0) {
                if (cachedFileId[0] == null) {
                    // Первая отправка — загружаем байты, получаем и кешируем file_id
                    cachedFileId[0] = telegramBot.sendPhotoBroadcastMessageUpload(user.getTelegramId(),
                            photoBytes,
                            photoFileName,
                            personalizedMessage,
                            gift);
                } else {
                    // Последующие отправки — только file_id, без загрузки байтов
                    telegramBot.sendPhotoBroadcastMessage(
                            user.getTelegramId(), cachedFileId[0], personalizedMessage, gift);
                }
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
