package ru.sapa.gadalka_backend.api.dto.feedback;

import ru.sapa.gadalka_backend.domain.SupportTicket;

import java.time.OffsetDateTime;

public record TicketSummaryResponse(
        Long id,
        Long userId,
        String userName,
        String status,
        OffsetDateTime createdAt,
        String descriptionPreview
) {
    public static TicketSummaryResponse from(SupportTicket ticket, String userName) {
        String preview = ticket.getDescription().length() > 100
                ? ticket.getDescription().substring(0, 100) + "..."
                : ticket.getDescription();
        return new TicketSummaryResponse(
                ticket.getId(),
                ticket.getUserId(),
                userName,
                ticket.getStatus().name(),
                ticket.getCreatedAt(),
                preview
        );
    }
}
