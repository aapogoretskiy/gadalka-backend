package ru.sapa.gadalka_backend.exception;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String code) {
        super("Продукт не найден: code=" + code);
    }
}
