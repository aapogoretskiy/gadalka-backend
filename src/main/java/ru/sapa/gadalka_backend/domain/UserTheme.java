package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Запись о том, что пользователь владеет темой.
 * Создаётся при покупке темы. Для бесплатных тем (is_free = true) не создаётся.
 */
@Getter
@Setter
@Entity
@Table(name = "user_themes")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserTheme {

    @EmbeddedId
    private UserThemeId id;

    @Column(name = "purchased_at", nullable = false)
    private OffsetDateTime purchasedAt;

    @PrePersist
    void prePersist() {
        if (purchasedAt == null) {
            purchasedAt = OffsetDateTime.now();
        }
    }
}
