package ru.sapa.gadalka_backend.service.feedback;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import ru.sapa.gadalka_backend.domain.type.FeedbackTargetType;
import ru.sapa.gadalka_backend.repository.FortuneRepository;

@Component
@RequiredArgsConstructor
public class FortuneFeedbackValidator implements FeedbackTargetValidator {

    private final FortuneRepository fortuneRepository;

    @Override
    public FeedbackTargetType getType() {
        return FeedbackTargetType.FORTUNE;
    }

    @Override
    public void validateOwnership(Long userId, Long actionId) {
        if (!fortuneRepository.existsByIdAndUserId(actionId, userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Гадание не найдено");
        }
    }
}
