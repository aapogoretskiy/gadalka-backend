package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

/**
 * Составной первичный ключ для таблицы user_themes.
 * JPA требует отдельный класс для составных ключей — он должен
 * реализовывать Serializable и корректно реализовывать equals/hashCode.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserThemeId implements Serializable {

    private Long userId;
    private Long themeId;
}
