package ru.sapa.gadalka_backend.api.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BalanceResponse {

    /** Текущее количество знаков на балансе */
    private int balance;

    /** true если есть активная подписка */
    private boolean hasActiveSubscription;

    /**
     * Доступна ли пользователю покупка подписок.
     * Пока фича в закрытом тесте — true только для админов (whitelist);
     * фронт показывает остальным вкладку «Подписки» задизейбленной.
     */
    private boolean subscriptionsAvailable;
}
