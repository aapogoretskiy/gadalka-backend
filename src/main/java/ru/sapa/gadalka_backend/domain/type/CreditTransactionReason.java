package ru.sapa.gadalka_backend.domain.type;

public enum CreditTransactionReason {
    /** Начисление за успешный платёж */
    PAYMENT,
    /** Списание при использовании функции */
    FEATURE_SPEND,
    /** Бесплатное начальное гадание при регистрации */
    FREE_GRANT,
    /** Возврат кредитов при рефанде */
    REFUND
}
