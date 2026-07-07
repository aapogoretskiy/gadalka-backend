package ru.sapa.gadalka_backend.service.feedback;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import ru.sapa.gadalka_backend.domain.type.FeedbackTargetType;
import ru.sapa.gadalka_backend.repository.NumerologyMonthReadingRepository;

@Component
@RequiredArgsConstructor
public class NumerologyMonthFeedbackValidator implements FeedbackTargetValidator {

    private final NumerologyMonthReadingRepository numerologyMonthReadingRepository;

    @Override
    public FeedbackTargetType getType() {
        return FeedbackTargetType.NUMEROLOGY_MONTH;
    }

    @Override
    public void validateOwnership(Long userId, Long actionId) {
        if (numerologyMonthReadingRepository.findByIdAndUserId(actionId, userId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Разбор на месяц не найден");
        }
    }
}
