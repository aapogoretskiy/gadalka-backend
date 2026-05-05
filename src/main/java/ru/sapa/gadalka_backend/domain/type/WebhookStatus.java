package ru.sapa.gadalka_backend.domain.type;

public enum WebhookStatus {
    /** Получен, ещё не обработан (ack-сервис сохранил, scheduler ещё не взял) */
    PENDING,
    /** Успешно обработан: платёж обновлён, кредиты начислены */
    PROCESSED,
    /** Обработка завершилась ошибкой, см. error_message */
    FAILED
}
