package ru.sapa.gadalka_backend.api.dto.feedback;

public record CloseTicketResponse(
        String message,
        Long ticketId,
        int creditsGifted
) {}
