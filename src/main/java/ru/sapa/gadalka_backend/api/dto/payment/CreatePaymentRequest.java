package ru.sapa.gadalka_backend.api.dto.payment;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreatePaymentRequest {

    @NotBlank(message = "Код продукта обязателен")
    private String productCode;
}
