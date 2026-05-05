package ru.sapa.gadalka_backend.api.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BalanceResponse {

    /** Текущее количество гаданий на балансе */
    private int balance;

    /** true если есть активная подписка (сейчас всегда false) */
    private boolean hasActiveSubscription;
}
