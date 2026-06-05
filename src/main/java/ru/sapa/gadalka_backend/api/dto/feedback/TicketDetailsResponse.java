package ru.sapa.gadalka_backend.api.dto.feedback;

import ru.sapa.gadalka_backend.domain.SupportTicket;
import ru.sapa.gadalka_backend.domain.User;

import java.time.OffsetDateTime;

public record TicketDetailsResponse(
        Long id,
        String description,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime closedAt,
        int creditsGifted,
        TicketUserInfo user
) {
    public record TicketUserInfo(
            Long id,
            Long telegramId,
            String username,
            String firstName
    ) {}

    public static TicketDetailsResponse from(SupportTicket ticket, User user) {
        return new TicketDetailsResponse(
                ticket.getId(),
                ticket.getDescription(),
                ticket.getStatus().name(),
                ticket.getCreatedAt(),
                ticket.getClosedAt(),
                ticket.getCreditsGifted() != null ? ticket.getCreditsGifted() : 0,
                new TicketUserInfo(
                        user.getId(),
                        user.getTelegramId(),
                        user.getUsername() != null ? user.getUsername() : "",
                        user.getFirstName() != null ? user.getFirstName() : ""
                )
        );
    }
}
