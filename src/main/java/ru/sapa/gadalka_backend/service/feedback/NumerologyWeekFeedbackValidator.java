package ru.sapa.gadalka_backend.service.feedback;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import ru.sapa.gadalka_backend.domain.type.FeedbackTargetType;
import ru.sapa.gadalka_backend.repository.NumerologyWeekReadingRepository;

@Component
@RequiredArgsConstructor
public class NumerologyWeekFeedbackValidator implements FeedbackTargetValidator {

    private final NumerologyWeekReadingRepository numerologyWeekReadingRepository;

    @Override
    public FeedbackTargetType getType() {
        return FeedbackTargetType.NUMEROLOGY_WEEK;
    }

    @Override
    public void validateOwnership(Long userId, Long actionId) {
        if (numerologyWeekReadingRepository.findByIdAndUserId(actionId, userId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Расклад на неделю не найден");
        }
    }
}
