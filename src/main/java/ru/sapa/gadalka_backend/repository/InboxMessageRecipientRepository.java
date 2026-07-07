package ru.sapa.gadalka_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sapa.gadalka_backend.api.dto.inbox.InboxMessageDto;
import ru.sapa.gadalka_backend.domain.InboxMessageRecipient;

import java.time.OffsetDateTime;

public interface InboxMessageRecipientRepository extends JpaRepository<InboxMessageRecipient, Long> {

    /** Счётчик для шилдика на кнопке "Входящие" в Profile. */
    long countByUserIdAndReadAtIsNull(Long userId);

    /**
     * Список сообщений конкретного пользователя, новые сверху.
     * JOIN с InboxMessage за текстом — без @ManyToOne, join по ON (см. InboxMessageRecipient).
     */
    @Query("SELECT new ru.sapa.gadalka_backend.api.dto.inbox.InboxMessageDto(" +
            "m.id, m.text, m.createdAt, CASE WHEN r.readAt IS NOT NULL THEN true ELSE false END) " +
            "FROM InboxMessageRecipient r JOIN InboxMessage m ON m.id = r.messageId " +
            "WHERE r.userId = :userId " +
            "ORDER BY m.createdAt DESC")
    Page<InboxMessageDto> findInboxForUser(@Param("userId") Long userId, Pageable pageable);

    /**
     * Отмечает прочитанными разом все непрочитанные сообщения пользователя —
     * вызывается при каждом заходе на вкладку "Входящие" (см. обсуждение: решили
     * не делать read-статус для каждого сообщения по отдельности, для простоты).
     */
    @Modifying
    @Query("UPDATE InboxMessageRecipient r SET r.readAt = :now WHERE r.userId = :userId AND r.readAt IS NULL")
    int markAllRead(@Param("userId") Long userId, @Param("now") OffsetDateTime now);
}
