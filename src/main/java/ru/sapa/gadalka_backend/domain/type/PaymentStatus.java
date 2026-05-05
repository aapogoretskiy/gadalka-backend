package ru.sapa.gadalka_backend.domain.type;

public enum PaymentStatus {
    /** Платёж создан, ожидаем подтверждения от провайдера */
    PENDING,
    /** Провайдер подтвердил успешную оплату, кредиты начислены */
    SUCCEEDED,
    /** Платёж отклонён провайдером или истёк */
    FAILED,
    /** Платёж отменён пользователем */
    CANCELLED
}
