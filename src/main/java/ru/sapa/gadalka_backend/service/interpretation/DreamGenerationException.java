package ru.sapa.gadalka_backend.service.interpretation;

/**
 * Бросается {@link AiInterpretationService#interpretDream}, когда AI не вернул валидный
 * JSON-разбор сна после всех повторных попыток.
 *
 * <p>В отличие от гороскопа (где есть вчерашний кэш), у разбора сна fallback-контента нет:
 * вызывающий код ({@code DreamService}) должен показать пользователю ошибку,
 * НЕ списывая знаки — списание происходит только после успешной генерации.
 */
public class DreamGenerationException extends RuntimeException {
    public DreamGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
