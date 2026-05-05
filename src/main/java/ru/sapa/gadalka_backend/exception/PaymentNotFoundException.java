package ru.sapa.gadalka_backend.exception;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(Long id) {
        super("Платёж не найден: id=" + id);
    }

    public PaymentNotFoundException(String providerPaymentId) {
        super("Платёж не найден: providerPaymentId=" + providerPaymentId);
    }
}
