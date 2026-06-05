package ru.sapa.gadalka_backend.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.sapa.gadalka_backend.api.dto.feedback.FeedbackRequest;
import ru.sapa.gadalka_backend.api.dto.feedback.FeedbackResponse;
import ru.sapa.gadalka_backend.domain.SupportTicket;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.service.SupportTicketService;

/**
 * Обратная связь от пользователей Mini App.
 *
 * <p>Защищён стандартным JwtAuthFilter — требует валидный Bearer-токен.
 */
@Slf4j
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController extends BaseController {

    private final SupportTicketService supportTicketService;

    /**
     * POST /api/feedback
     *
     * <p>Создаёт заявку обратной связи от текущего пользователя.
     * Лимит: не более 3 открытых заявок одновременно.
     *
     * @return 201 Created с id и статусом заявки
     */
    @PostMapping
    public ResponseEntity<FeedbackResponse> createFeedback(
            @Valid @RequestBody FeedbackRequest request,
            HttpServletRequest httpRequest) {

        User user = resolveUser(httpRequest);
        SupportTicket ticket = supportTicketService.createTicket(user.getId(), request.description());

        log.info("Пользователь userId={} создал заявку ticketId={}", user.getId(), ticket.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(FeedbackResponse.from(ticket));
    }
}
