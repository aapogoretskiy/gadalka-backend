package ru.sapa.gadalka_backend.service.feedback;

import ru.sapa.gadalka_backend.domain.type.FeedbackTargetType;

/**
 * Стратегия валидации цели фидбэка.
 *
 * <p>Каждая реализация отвечает за один тип действия (FORTUNE, COMPATIBILITY и т.д.).
 * Чтобы добавить поддержку нового типа — достаточно создать новый {@code @Component},
 * реализующий этот интерфейс. Существующий код при этом не изменяется (Open/Closed).
 *
 * <p>Spring автоматически соберёт все реализации в {@code List<FeedbackTargetValidator>},
 * откуда {@link FeedbackService} строит диспетчерскую карту по типу.
 */
public interface FeedbackTargetValidator {

    /** Тип действия, за который отвечает эта стратегия */
    FeedbackTargetType getType();

    /**
     * Проверяет, что действие с указанным id принадлежит пользователю.
     * Бросает исключение, если запись не найдена или чужая.
     */
    void validateOwnership(Long userId, Long actionId);
}
