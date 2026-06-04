package ru.sapa.gadalka_backend.api.dto.payment;

import lombok.Builder;
import lombok.Getter;
import ru.sapa.gadalka_backend.domain.PaymentProduct;

@Getter
@Builder
public class PaymentProductDto {

    private String code;
    private String name;
    private int readingsCount;
    /** Бонусные знаки сверх пакета (0 для большинства, 2 для PACK_15) */
    private int bonusCredits;
    /** Цена в рублях (не копейках) — для отображения пользователю */
    private double priceRub;
    private int priceStars;

    public static PaymentProductDto from(PaymentProduct product) {
        return PaymentProductDto.builder()
                .code(product.getCode())
                .name(product.getName())
                .readingsCount(product.getReadingsCount())
                .bonusCredits(product.getBonusCredits())
                .priceRub(product.getPriceRub() / 100.0)
                .priceStars(product.getPriceStars())
                .build();
    }
}
