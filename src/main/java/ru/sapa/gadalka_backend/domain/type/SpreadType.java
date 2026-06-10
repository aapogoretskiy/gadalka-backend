package ru.sapa.gadalka_backend.domain.type;

import lombok.Getter;

/**
 * Тип таро-расклада.
 * Определяет количество карт и стоимость в кредитах.
 */
@Getter
public enum SpreadType {

    /** Классика: Прошлое · Настоящее · Будущее (3 карты, 3 знака) */
    THREE_CARD(3),

    /** Подкова (7 карт, 6 знаков) */
    HORSESHOE(6),

    /** Кельтский крест (10 карт, 9 знаков) */
    CELTIC_CROSS(9);

    private final int creditCost;

    SpreadType(int creditCost) {
        this.creditCost = creditCost;
    }

}
