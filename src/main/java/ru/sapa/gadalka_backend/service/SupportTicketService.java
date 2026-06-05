package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.bot.GadalkaTelegramBot;
import ru.sapa.gadalka_backend.domain.SupportTicket;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.type.CreditTransactionReason;
import ru.sapa.gadalka_backend.domain.type.SupportTicketStatus;
import ru.sapa.gadalka_backend.exception.LimitExceededException;
import ru.sapa.gadalka_backend.repository.SupportTicketRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Управление заявками обратной связи.
 *
 * <p>Бизнес-правила:
 * <ul>
 *   <li>Пользователь не может иметь более 3 открытых заявок одновременно.</li>
 *   <li>При закрытии с подарком: начисляются кредиты + отправляется уведомление в Telegram.</li>
 *   <li>При закрытии без подарка: молчаливое закрытие, пользователь не уведомляется.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportTicketService {

    private static final int MAX_OPEN_TICKETS_PER_USER = 3;

    private final SupportTicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final FortuneCreditService fortuneCreditService;
    private final GadalkaTelegramBot telegramBot;

    /**
     * Создаёт новую заявку от пользователя.
     *
     * @throws LimitExceededException если у пользователя уже 3 открытые заявки
     */
    @Transactional
    public SupportTicket createTicket(Long userId, String description) {
        long openCount = ticketRepository.countByUserIdAndStatus(userId, SupportTicketStatus.OPEN);
        if (openCount >= MAX_OPEN_TICKETS_PER_USER) {
            throw new LimitExceededException(
                    "У вас уже " + MAX_OPEN_TICKETS_PER_USER + " открытых заявок. " +
                    "Дождитесь рассмотрения текущих обращений."
            );
        }

        SupportTicket ticket = SupportTicket.builder()
                .userId(userId)
                .description(description)
                .build();

        SupportTicket saved = ticketRepository.save(ticket);
        log.info("Создана заявка поддержки: ticketId={}, userId={}", saved.getId(), userId);
        return saved;
    }

    /**
     * Закрывает заявку администратором.
     *
     * <p>Если {@code creditsToGift > 0}, начисляет кредиты пользователю
     * и отправляет ему уведомление через Telegram-бот.
     *
     * @param ticketId     ID заявки
     * @param adminId      telegramId администратора (для логов)
     * @param creditsToGift количество знаков для подарка (0 или null — без подарка)
     * @return закрытая заявка или empty если не найдена
     */
    @Transactional
    public Optional<SupportTicket> closeTicket(Long ticketId, Long adminId, Integer creditsToGift) {
        Optional<SupportTicket> ticketOpt = ticketRepository.findById(ticketId);
        if (ticketOpt.isEmpty()) {
            return Optional.empty();
        }

        SupportTicket ticket = ticketOpt.get();

        if (ticket.getStatus() == SupportTicketStatus.CLOSED) {
            log.warn("Admin {} попытался закрыть уже закрытую заявку ticketId={}", adminId, ticketId);
            return Optional.of(ticket);
        }

        ticket.setStatus(SupportTicketStatus.CLOSED);
        ticket.setClosedAt(OffsetDateTime.now());

        if (creditsToGift != null && creditsToGift > 0) {
            ticket.setCreditsGifted(creditsToGift);

            fortuneCreditService.grantCredits(
                    ticket.getUserId(),
                    creditsToGift,
                    CreditTransactionReason.SUPPORT_GIFT,
                    null
            );

            // Получаем telegramId пользователя для отправки уведомления
            userRepository.findById(ticket.getUserId()).ifPresent(user ->
                    telegramBot.sendSupportClosedWithGift(user.getTelegramId(), creditsToGift)
            );

            log.info("Admin {} закрыл заявку ticketId={} с подарком {} знаков", adminId, ticketId, creditsToGift);
        } else {
            log.info("Admin {} закрыл заявку ticketId={} без подарка", adminId, ticketId);
        }

        return Optional.of(ticketRepository.save(ticket));
    }

    /**
     * Список заявок для админ-панели с фильтрацией по статусу.
     *
     * @param status фильтр по статусу (null — все заявки)
     * @param page   номер страницы (0-based)
     * @param size   размер страницы
     */
    @Transactional(readOnly = true)
    public Page<SupportTicket> getTickets(SupportTicketStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        if (status != null) {
            return ticketRepository.findAllByStatusOrderByCreatedAtDesc(status, pageable);
        }
        return ticketRepository.findAll(pageable);
    }

    /** Получить заявку по ID */
    @Transactional(readOnly = true)
    public Optional<SupportTicket> getTicket(Long ticketId) {
        return ticketRepository.findById(ticketId);
    }
}
