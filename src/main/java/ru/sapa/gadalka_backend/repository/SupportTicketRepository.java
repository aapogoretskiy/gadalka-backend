package ru.sapa.gadalka_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.sapa.gadalka_backend.domain.SupportTicket;
import ru.sapa.gadalka_backend.domain.type.SupportTicketStatus;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    /** Количество открытых заявок пользователя — для проверки лимита */
    long countByUserIdAndStatus(Long userId, SupportTicketStatus status);

    /** Список заявок с фильтром по статусу для админ-панели */
    Page<SupportTicket> findAllByStatusOrderByCreatedAtDesc(SupportTicketStatus status, Pageable pageable);
}
