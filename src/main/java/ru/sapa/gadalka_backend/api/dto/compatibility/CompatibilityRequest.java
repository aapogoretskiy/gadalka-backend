package ru.sapa.gadalka_backend.api.dto.compatibility;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class CompatibilityRequest {

    /**
     * Список участников для анализа совместимости.
     * Необходимо указать ровно двух людей.
     *
     * <p><b>Важно об именах:</b> Для получения стабильного и идемпотентного результата
     * рекомендуется всегда вводить <b>полное официальное имя</b> (например, «Александр»,
     * а не «Саша»). Система не распознаёт связь между краткими и полными формами имён —
     * «Саша» и «Александр» будут считаться разными людьми и дадут разные расклады.
     */
    @NotNull(message = "Список участников обязателен")
    @Size(min = 2, max = 2, message = "Необходимо указать ровно двух участников")
    @Valid
    private List<PersonInput> persons;

    @Getter
    @Setter
    public static class PersonInput {

        /**
         * Полное официальное имя участника.
         * Допускаются только буквы (русские или латинские), дефис и пробел.
         * Краткие формы (Саша, Катя, Вася) допустимы технически, но для
         * корректной идемпотентности рекомендуется использовать полные имена.
         */
        @NotBlank(message = "Имя не может быть пустым")
        @Size(min = 2, max = 50, message = "Имя должно содержать от 2 до 50 символов")
        @Pattern(
                regexp = "^[а-яёА-ЯЁa-zA-Z][а-яёА-ЯЁa-zA-Z\\s\\-]*$",
                message = "Имя должно содержать только буквы"
        )
        private String name;

        @NotNull(message = "Дата рождения обязательна")
        @Past(message = "Дата рождения должна быть в прошлом")
        private LocalDate birthDate;
    }
}
