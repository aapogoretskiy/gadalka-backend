package ru.sapa.gadalka_backend.exception;

/**
 * Выбрасывается когда пользователь пытается списать квоту подписки,
 * но квота исчерпана (или отсутствует для данной фичи / нет активной подписки).
 */
public class QuotaExceededException extends RuntimeException {

    public QuotaExceededException(String message) {
        super(message);
    }

    public static QuotaExceededException noActiveSubscription() {
        return new QuotaExceededException("Нет активной подписки");
    }

    public static QuotaExceededException noQuotaForFeature() {
        return new QuotaExceededException("Подписка не включает квоту на эту функцию");
    }

    public static QuotaExceededException quotaExhausted() {
        return new QuotaExceededException("Квота подписки на эту функцию исчерпана");
    }

    /** Скрытый дневной анти-абьюз лимит безлимитной квоты (см. SubscriptionQuota.isUnlimited) */
    public static QuotaExceededException dailyLimitReached() {
        return new QuotaExceededException("Звезды исчерпали энергию на сегодня по данному типу гаданий — попробуйте завтра");
    }
}
