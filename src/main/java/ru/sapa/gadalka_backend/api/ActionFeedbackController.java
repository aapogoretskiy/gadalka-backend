package ru.sapa.gadalka_backend.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.sapa.gadalka_backend.api.dto.action_feedback.ActionFeedbackRequest;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.type.FeedbackTargetType;
import ru.sapa.gadalka_backend.service.feedback.FeedbackService;

/**
 * POST /api/action-feedback/{type}/{actionId}
 *
 * <p>Принимает оценку пользователя на платное действие
 */
@Slf4j
@RestController
@RequestMapping("/api/action-feedback")
@RequiredArgsConstructor
public class ActionFeedbackController extends BaseController {

    private final FeedbackService feedbackService;

    /**
     * Отправить (или обновить) оценку на действие.
     *
     * @param type      тип действия: FORTUNE, COMPATIBILITY (enum-значение в пути)
     * @param actionId  id записи в соответствующей таблице
     * @param body      тело с rating (обязательно) и comment (опционально)
     * @param request   HTTP-запрос — нужен для получения текущего пользователя
     */
    @PostMapping("/{type}/{actionId}")
    public ResponseEntity<Void> submitFeedback(
            @PathVariable FeedbackTargetType type,
            @PathVariable Long actionId,
            @Valid @RequestBody ActionFeedbackRequest body,
            HttpServletRequest request
    ) {
        User user = resolveUser(request);

        feedbackService.submitFeedback(
                user.getId(),
                type,
                actionId,
                body.rating(),
                body.comment()
        );

        log.info("Фидбэк принят: userId={}, type={}, actionId={}, rating={}",
                user.getId(),
                type,
                actionId,
                body.rating());

        return ResponseEntity.ok().build();
    }
}
