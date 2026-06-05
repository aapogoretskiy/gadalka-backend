package ru.sapa.gadalka_backend.api.dto.feedback;

import ru.sapa.gadalka_backend.domain.SupportTicket;

import java.time.OffsetDateTime;

public record FeedbackResponse(
        Long id,
        String status,
        OffsetDateTime createdAt
) {
    public static FeedbackResponse from(SupportTicket ticket) {
        return new FeedbackResponse(
                ticket.getId(),
                ticket.getStatus().name(),
                ticket.getCreatedAt()
        );
    }
}
