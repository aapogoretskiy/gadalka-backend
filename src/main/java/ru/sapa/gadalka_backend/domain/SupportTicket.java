package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;
import ru.sapa.gadalka_backend.domain.type.SupportTicketStatus;

import java.time.OffsetDateTime;

/**
 * Заявка обратной связи от пользователя.
 *
 * <p>Пользователь описывает проблему через профиль Mini App.
 * Администратор видит заявки в панели и может закрыть их,
 * опционально подарив пользователю знаки в качестве компенсации.
 */
@Entity
@Table(name = "support_tickets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Владелец заявки */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Текст обращения от пользователя */
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SupportTicketStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /** Проставляется в момент закрытия заявки администратором */
    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    /**
     * Количество знаков, подаренных при закрытии.
     * Null — если заявка ещё не закрыта или закрыта без подарка.
     */
    @Column(name = "credits_gifted")
    private Integer creditsGifted;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
        status = SupportTicketStatus.OPEN;
    }
}
