package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.api.dto.admin.InboxMessageStatsDto;
import ru.sapa.gadalka_backend.api.dto.inbox.InboxMessageDto;
import ru.sapa.gadalka_backend.domain.InboxMessage;
import ru.sapa.gadalka_backend.domain.InboxMessageRecipient;
import ru.sapa.gadalka_backend.repository.InboxMessageRecipientRepository;
import ru.sapa.gadalka_backend.repository.InboxMessageRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Сервис "Входящих" — второй, гарантированный канал доставки админских сообщений
 * внутри самого приложения, не зависящий от Telegram API (см. миграцию V60).
 * <p>
 * В отличие от {@link BroadcastService}, тут нет начисления подарочных знаков —
 * сознательно решили не усложнять: подарок доступен только при отправке в Telegram
 * (см. AdminController#broadcast — проверка на toInbox + giftAmount).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboxService {

    /** Размер пачки при батч-вставке получателей — чтобы не строить один гигантский INSERT. */
    private static final int BATCH_SIZE = 500;

    private final InboxMessageRepository messageRepository;
    private final InboxMessageRecipientRepository recipientRepository;

    /**
     * Создаёт сообщение и рассылает его во "Входящие" всем переданным пользователям.
     *
     * @param text               текст сообщения
     * @param userIds            внутренние ID получателей (уже резолвнутая аудитория —
     *                           см. AdminController#broadcast)
     * @param adminTelegramId    telegramId администратора, запустившего рассылку (для аудита)
     */
    @Async
    @Transactional
    public void send(String text, List<Long> userIds, Long adminTelegramId) {
        InboxMessage message = messageRepository.save(InboxMessage.builder()
                .text(text)
                .createdByAdminTelegramId(adminTelegramId)
                .build());

        for (int from = 0; from < userIds.size(); from += BATCH_SIZE) {
            int to = Math.min(from + BATCH_SIZE, userIds.size());
            List<InboxMessageRecipient> batch = userIds.subList(from, to).stream()
                    .map(userId -> InboxMessageRecipient.builder()
                            .messageId(message.getId())
                            .userId(userId)
                            .build())
                    .toList();
            recipientRepository.saveAll(batch);
        }

        log.info("Сообщение во Входящие отправлено: messageId={}, получателей={}", message.getId(), userIds.size());
    }

    public long countUnread(Long userId) {
        return recipientRepository.countByUserIdAndReadAtIsNull(userId);
    }

    public Page<InboxMessageDto> listForUser(Long userId, Pageable pageable) {
        return recipientRepository.findInboxForUser(userId, pageable);
    }

    @Transactional
    public void markAllRead(Long userId) {
        recipientRepository.markAllRead(userId, OffsetDateTime.now());
    }

    public Page<InboxMessageStatsDto> getMessageStats(Pageable pageable) {
        return messageRepository.findMessageStats(pageable);
    }
}
