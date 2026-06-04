package ru.sapa.gadalka_backend.domain.type;

import lombok.Getter;

/**
 * Тип таро-расклада.
 * Определяет количество карт и стоимость в кредитах.
 */
@Getter
public enum SpreadType {

    /** Классика: Прошлое · Настоящее · Будущее (3 карты, 2 знака) */
    THREE_CARD(2),

    /** Подкова (7 карт, 4 знака) */
    HORSESHOE(4),

    /** Кельтский крест (10 карт, 6 знаков) */
    CELTIC_CROSS(6);

    private final int creditCost;

    SpreadType(int creditCost) {
        this.creditCost = creditCost;
    }

}
