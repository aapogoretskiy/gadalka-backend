package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.bot.GadalkaTelegramBot;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.type.CreditTransactionReason;
import ru.sapa.gadalka_backend.repository.UserRepository;

import java.util.List;

/**
 * Сервис массовой рассылки сообщений через Telegram-бот.
 *
 * <p>Все методы помечены {@code @Async} — рассылка запускается в фоновом потоке
 * и не блокирует HTTP-запрос администратора.
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

    private final UserRepository userRepository;
    private final FortuneCreditService fortuneCreditService;
    private final GadalkaTelegramBot telegramBot;

    /**
     * Запускает рассылку в фоновом потоке.
     *
     * @param message    текст сообщения для отправки в Telegram
     * @param giftAmount количество знаков для начисления (null или 0 — не начислять)
     * @param userIds    список внутренних ID пользователей; если null или пусто — рассылка всем
     */
    @Async
    public void broadcast(String message, Integer giftAmount, List<Long> userIds) {
        boolean giftEnabled = giftAmount != null && giftAmount > 0;
        boolean toAll = userIds == null || userIds.isEmpty();

        log.info("Рассылка запущена: toAll={}, recipients={}, giftAmount={}, message.length={}",
                toAll, toAll ? "all" : userIds.size(), giftAmount, message.length());

        if (toAll) {
            broadcastToAll(message, giftEnabled, giftAmount);
        } else {
            broadcastToSelected(message, giftEnabled, giftAmount, userIds);
        }
    }

    /**
     * Рассылка всем пользователям батчами, чтобы не грузить память при большой базе.
     */
    private void broadcastToAll(String message, boolean giftEnabled, Integer giftAmount) {
        int page = 0;
        int sent = 0;
        int failed = 0;

        while (true) {
            List<User> batch = userRepository.findAll(PageRequest.of(page, BATCH_SIZE)).getContent();
            if (batch.isEmpty()) break;

            for (User user : batch) {
                boolean ok = sendToUser(user, message, giftEnabled, giftAmount);
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
    private void broadcastToSelected(String message, boolean giftEnabled, Integer giftAmount, List<Long> userIds) {
        List<User> users = userRepository.findAllById(userIds);
        int sent = 0;
        int failed = 0;

        for (User user : users) {
            boolean ok = sendToUser(user, message, giftEnabled, giftAmount);
            if (ok) sent++; else failed++;
            sleep();
        }

        log.info("Рассылка завершена (выбранным): запрошено={}, отправлено={}, ошибок={}", userIds.size(), sent, failed);
    }

    /**
     * Отправляет сообщение одному пользователю и при необходимости начисляет знаки.
     *
     * @return true если сообщение отправлено успешно (ошибки начисления тоже логируются, но не меняют результат)
     */
    private boolean sendToUser(User user, String message, boolean giftEnabled, Integer giftAmount) {
        try {
            if (giftEnabled) {
                fortuneCreditService.grantCredits(user.getId(),
                        giftAmount,
                        CreditTransactionReason.ADMIN_BROADCAST,
                        null);
            }
            telegramBot.sendBroadcastMessage(user.getTelegramId(), message, giftEnabled ? giftAmount : null);
            return true;
        } catch (Exception e) {
            log.warn("Ошибка рассылки: userId={}, telegramId={}, error={}", user.getId(), user.getTelegramId(), e.getMessage());
            return false;
        }
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
