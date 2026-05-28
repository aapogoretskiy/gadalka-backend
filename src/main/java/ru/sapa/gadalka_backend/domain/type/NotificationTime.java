package ru.sapa.gadalka_backend.domain.type;

/**
 * Время получения уведомлений от бота.
 * MORNING - 09:00 по Москве
 * EVENING - 20:00 по Москве
 * DISABLED - уведомления отключены
 */
public enum NotificationTime {
    MORNING,
    EVENING,
    DISABLED
}
