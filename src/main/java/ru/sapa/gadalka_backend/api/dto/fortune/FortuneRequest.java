package ru.sapa.gadalka_backend.api.dto.fortune;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.sapa.gadalka_backend.domain.type.SpreadType;

@Getter
@Setter
@NoArgsConstructor
public class FortuneRequest {

    @NotBlank(message = "Вопрос не может быть пустым")
    @Size(max = 500, message = "Вопрос не должен превышать 500 символов")
    private String question;

    /**
     * Категория гадания. Допустимые значения: love, money, work, life, health, ex, intimacy.
     * Может быть null — тогда интерпретация без привязки к сфере.
     */
    @Pattern(
            regexp = "^(love|money|work|life|health|ex|intimacy)$",
            message = "Категория должна быть одной из: love, money, work, life, health, ex, intimacy"
    )
    private String category;

    /**
     * Тип расклада. По умолчанию THREE_CARD для обратной совместимости.
     */
    @NotNull(message = "Тип расклада не может быть null")
    private SpreadType spreadType = SpreadType.THREE_CARD;
}
