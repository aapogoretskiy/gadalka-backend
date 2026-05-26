package ru.sapa.gadalka_backend.domain.type;

public enum CreditTransactionReason {
    /** Начисление за успешный платёж */
    PAYMENT,
    /** Списание при использовании функции */
    FEATURE_SPEND,
    /** Бесплатный начальный знак при регистрации */
    FREE_GRANT,
    /** Возврат кредитов при рефанде */
    REFUND,
    /** Покупка темы (колоды) карт */
    THEME_PURCHASE
}
