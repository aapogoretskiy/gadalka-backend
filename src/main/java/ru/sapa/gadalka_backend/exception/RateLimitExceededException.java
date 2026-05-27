package ru.sapa.gadalka_backend.exception;

/**
 * Выбрасывается когда пользователь превысил лимит запросов к AI.
 * Обрабатывается GlobalExceptionHandler → HTTP 429 Too Many Requests.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
