package ru.sapa.gadalka_backend.exception;

public class FreeFortuneAlreadyUsedException extends RuntimeException {
    public FreeFortuneAlreadyUsedException() {
        super("Бесплатное гадание уже использовано. Для продолжения необходима оплата.");
    }
}
