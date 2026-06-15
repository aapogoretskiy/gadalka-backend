package ru.sapa.gadalka_backend.service.feedback;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import ru.sapa.gadalka_backend.domain.type.FeedbackTargetType;
import ru.sapa.gadalka_backend.repository.CompatibilityReadingRepository;

@Component
@RequiredArgsConstructor
public class CompatibilityFeedbackValidator implements FeedbackTargetValidator {

    private final CompatibilityReadingRepository compatibilityReadingRepository;

    @Override
    public FeedbackTargetType getType() {
        return FeedbackTargetType.COMPATIBILITY;
    }

    @Override
    public void validateOwnership(Long userId, Long actionId) {
        if (!compatibilityReadingRepository.existsByIdAndUserId(actionId, userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Расклад совместимости не найден");
        }
    }
}
