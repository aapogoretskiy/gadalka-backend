package ru.sapa.gadalka_backend.api.dto.fortune;

import jakarta.validation.constraints.NotBlank;
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
}
