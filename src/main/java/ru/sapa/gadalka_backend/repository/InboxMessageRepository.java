package ru.sapa.gadalka_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.sapa.gadalka_backend.api.dto.admin.InboxMessageStatsDto;
import ru.sapa.gadalka_backend.domain.InboxMessage;

public interface InboxMessageRepository extends JpaRepository<InboxMessage, Long> {

    /**
     * История отправок во "Входящие" для админки — сколько получателей у каждого
     * сообщения и сколько из них прочитали. Один JOIN + GROUP BY вместо N+1 запросов.
     */
    @Query("SELECT new ru.sapa.gadalka_backend.api.dto.admin.InboxMessageStatsDto(" +
            "m.id, m.text, m.createdAt, COUNT(r.id), SUM(CASE WHEN r.readAt IS NOT NULL THEN 1L ELSE 0L END)) " +
            "FROM InboxMessage m JOIN InboxMessageRecipient r ON r.messageId = m.id " +
            "GROUP BY m.id, m.text, m.createdAt " +
            "ORDER BY m.createdAt DESC")
    Page<InboxMessageStatsDto> findMessageStats(Pageable pageable);
}
