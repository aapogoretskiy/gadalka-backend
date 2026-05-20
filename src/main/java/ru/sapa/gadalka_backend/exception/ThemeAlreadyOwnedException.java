package ru.sapa.gadalka_backend.exception;

public class ThemeAlreadyOwnedException extends RuntimeException {
    public ThemeAlreadyOwnedException() {
        super("Тема уже куплена");
    }
}
