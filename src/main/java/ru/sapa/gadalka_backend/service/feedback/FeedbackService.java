package ru.sapa.gadalka_backend.service.feedback;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.sapa.gadalka_backend.domain.ActionFeedback;
import ru.sapa.gadalka_backend.domain.type.FeedbackRating;
import ru.sapa.gadalka_backend.domain.type.FeedbackTargetType;
import ru.sapa.gadalka_backend.repository.ActionFeedbackRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Сервис фидбэка на платные действия.
 *
 * <p>Диспетчеризация по типу действия реализована через Strategy pattern:
 * все {@link FeedbackTargetValidator}-бины собираются Spring'ом автоматически,
 * {@code @PostConstruct} строит из них {@code Map<type → validator>}.
 *
 * <p>Добавление нового типа (например NUMEROLOGY_DAY):
 * <ol>
 *   <li>Добавить значение в {@link FeedbackTargetType}.</li>
 *   <li>Создать {@code @Component}, реализующий {@link FeedbackTargetValidator}.</li>
 * </ol>
 * Этот класс при этом не изменяется.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final List<FeedbackTargetValidator> validators;
    private final ActionFeedbackRepository feedbackRepository;

    /** Диспетчерская карта, построенная один раз при старте */
    private Map<FeedbackTargetType, FeedbackTargetValidator> validatorMap;

    @PostConstruct
    void init() {
        validatorMap = validators
                .stream()
                .collect(Collectors.toMap(FeedbackTargetValidator::getType, Function.identity()));
        log.info("FeedbackService: зарегистрированы валидаторы для типов: {}", validatorMap.keySet());
    }

    /**
     * Сохраняет фидбэк пользователя на действие.
     * Если фидбэк уже существует — обновляет его (idempotent).
     *
     * @param userId   id пользователя из JWT
     * @param type     тип действия (FORTUNE, COMPATIBILITY)
     * @param actionId id записи в соответствующей таблице
     * @param rating   оценка (POSITIVE / NEGATIVE)
     * @param comment  опциональный текст (только при NEGATIVE, иначе игнорируется)
     */
    @Transactional
    public void submitFeedback(Long userId,
                               FeedbackTargetType type,
                               Long actionId,
                               FeedbackRating rating,
                               String comment) {

        FeedbackTargetValidator validator = validatorMap.get(type);
        if (validator == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Фидбэк для типа " + type + " не поддерживается");
        }

        // Проверяем что действие принадлежит пользователю
        validator.validateOwnership(userId, actionId);

        // Комментарий имеет смысл только при отрицательной оценке
        String sanitizedComment = (rating == FeedbackRating.NEGATIVE && comment != null && !comment.isBlank())
                ? comment.strip()
                : null;

        Optional<ActionFeedback> existing = feedbackRepository.findByUserIdAndActionTypeAndActionId(userId, type, actionId);

        if (existing.isPresent()) {
            ActionFeedback fb = existing.get();
            fb.setRating(rating);
            fb.setComment(sanitizedComment);
            log.info("Фидбэк обновлён: userId={}, type={}, actionId={}, rating={}", userId, type, actionId, rating);
        } else {
            feedbackRepository.save(ActionFeedback.builder()
                    .userId(userId)
                    .actionType(type)
                    .actionId(actionId)
                    .rating(rating)
                    .comment(sanitizedComment)
                    .build());
            log.info("Фидбэк сохранён: userId={}, type={}, actionId={}, rating={}", userId, type, actionId, rating);
        }
    }
}
