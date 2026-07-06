package ru.sapa.gadalka_backend.service.feedback;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import ru.sapa.gadalka_backend.domain.type.FeedbackTargetType;
import ru.sapa.gadalka_backend.repository.DreamReadingRepository;

/**
 * Валидатор фидбэка для разборов снов (Сонник).
 * Регистрируется в {@link FeedbackService} автоматически как Spring-бин —
 * больше ничего для поддержки типа DREAM менять не нужно (Open/Closed).
 */
@Component
@RequiredArgsConstructor
public class DreamFeedbackValidator implements FeedbackTargetValidator {

    private final DreamReadingRepository dreamReadingRepository;

    @Override
    public FeedbackTargetType getType() {
        return FeedbackTargetType.DREAM;
    }

    @Override
    public void validateOwnership(Long userId, Long actionId) {
        if (!dreamReadingRepository.existsByIdAndUserId(actionId, userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Разбор сна не найден");
        }
    }
}
