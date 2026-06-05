package ru.sapa.gadalka_backend.exception;

/**
 * Выбрасывается когда пользователь превысил допустимый лимит (например, открытых заявок).
 * Обрабатывается GlobalExceptionHandler → HTTP 400 Bad Request.
 */
public class LimitExceededException extends RuntimeException {

    public LimitExceededException(String message) {
        super(message);
    }
}
