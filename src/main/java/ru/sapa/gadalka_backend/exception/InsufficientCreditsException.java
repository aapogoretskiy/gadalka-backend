package ru.sapa.gadalka_backend.exception;

/**
 * Выбрасывается когда у пользователя недостаточно гаданий (кредитов)
 * и нет активной подписки.
 */
public class InsufficientCreditsException extends RuntimeException {

    public InsufficientCreditsException() {
        super("Недостаточно гаданий. Пополните баланс для продолжения.");
    }
}
