package ru.sapa.gadalka_backend.domain.type;

/**
 * Тип цели фидбэка — определяет, к какой таблице относится action_id.
 * Расширяется добавлением нового значения + нового FeedbackTargetValidator-бина.
 * Существующий код при этом не меняется (принцип Open/Closed).
 */
public enum FeedbackTargetType {
    FORTUNE,
    COMPATIBILITY
}
