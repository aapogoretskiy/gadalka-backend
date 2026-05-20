package ru.sapa.gadalka_backend.exception;

public class ThemeNotFoundException extends RuntimeException {
    public ThemeNotFoundException(Long themeId) {
        super("Тема не найдена: id=" + themeId);
    }
}
