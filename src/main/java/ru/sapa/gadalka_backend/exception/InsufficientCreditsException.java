package ru.sapa.gadalka_backend.exception;

/**
 * Выбрасывается когда у пользователя недостаточно знаков (кредитов)
 * и нет активной подписки.
 */
public class InsufficientCreditsException extends RuntimeException {

    public InsufficientCreditsException() {
        super("Недостаточно знаков. Пополните баланс для продолжения.");
    }
}
