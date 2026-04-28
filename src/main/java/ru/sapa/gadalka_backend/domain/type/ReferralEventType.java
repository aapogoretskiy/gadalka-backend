package ru.sapa.gadalka_backend.domain.type;

/**
 * Тип реферального события.
 * <ul>
 *   <li>{@code BOT_ENTRY}  — пользователь перешёл по deep-link (?start=CODE) и бот получил команду /start</li>
 *   <li>{@code APP_OPEN}   — пользователь открыл Mini App с параметром start_param в initData</li>
 * </ul>
 */
public enum ReferralEventType {
    BOT_ENTRY,
    APP_OPEN
}
