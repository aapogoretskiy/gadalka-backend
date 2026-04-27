package ru.sapa.gadalka_backend.api.dto.fortune;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FortuneRequest {

    @NotBlank(message = "Вопрос не может быть пустым")
    @Size(max = 500, message = "Вопрос не должен превышать 500 символов")
    private String question;

    /**
     * Категория гадания. Допустимые значения: love, money, work, life, health.
     * Может быть null — тогда интерпретация без привязки к сфере.
     */
    @Pattern(
            regexp = "^(love|money|work|life|health)$",
            message = "Категория должна быть одной из: love, money, work, life, health"
    )
    private String category;
}
