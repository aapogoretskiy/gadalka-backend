package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * Продукт из каталога — то, что пользователь может купить.
 * Цены хранятся: rub в копейках (9900 = 99₽), stars — в штуках звёзд Telegram.
 */
@Entity
@Table(name = "payment_products")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Уникальный код продукта, например PACK_3, PACK_7, PACK_15 */
    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    /** Отображаемое название, например "7 гаданий" */
    @Column(name = "name", nullable = false)
    private String name;

    /** Сколько гаданий (кредитов) начисляется при покупке */
    @Column(name = "readings_count", nullable = false)
    private Integer readingsCount;

    /** Цена в копейках (для ЮKassa) */
    @Column(name = "price_rub", nullable = false)
    private Integer priceRub;

    /** Цена в звёздах Telegram */
    @Column(name = "price_stars", nullable = false)
    private Integer priceStars;

    /** Скрытые продукты не отображаются в каталоге */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    /** Порядок отображения в каталоге (меньше = выше) */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
