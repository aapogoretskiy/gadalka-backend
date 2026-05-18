package ru.sapa.gadalka_backend.domain.type;

import lombok.Getter;

/**
 * Тип таро-расклада.
 * Определяет количество карт и стоимость в кредитах.
 */
@Getter
public enum SpreadType {

    /** Классика: Прошлое · Настоящее · Будущее (3 карты, 1 кредит) */
    THREE_CARD(1),

    /** Подкова (7 карт, 2 кредита) */
    HORSESHOE(2),

    /** Кельтский крест (10 карт, 3 кредита) */
    CELTIC_CROSS(3);

    private final int creditCost;

    SpreadType(int creditCost) {
        this.creditCost = creditCost;
    }

}
