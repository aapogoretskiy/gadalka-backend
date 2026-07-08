package ru.sapa.gadalka_backend.service.feedback;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import ru.sapa.gadalka_backend.domain.type.FeedbackTargetType;
import ru.sapa.gadalka_backend.repository.NumerologyYearReadingRepository;

@Component
@RequiredArgsConstructor
public class NumerologyYearFeedbackValidator implements FeedbackTargetValidator {

    private final NumerologyYearReadingRepository numerologyYearReadingRepository;

    @Override
    public FeedbackTargetType getType() {
        return FeedbackTargetType.NUMEROLOGY_YEAR;
    }

    @Override
    public void validateOwnership(Long userId, Long actionId) {
        if (numerologyYearReadingRepository.findByIdAndUserId(actionId, userId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Разбор на год не найден");
        }
    }
}
