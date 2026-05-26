package ru.sapa.gadalka_backend.exception;

public class FreeFortuneAlreadyUsedException extends RuntimeException {
    public FreeFortuneAlreadyUsedException() {
        super("Бесплатный знак уже использован. Для продолжения необходима оплата.");
    }
}
