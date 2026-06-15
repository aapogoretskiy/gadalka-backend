package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Лог посещений пользователя.
 * Одна запись = один "сеанс" — создаётся JwtAuthFilter при обновлении lastActiveAt
 * (то есть не чаще раза в 5 минут реального времени использования).
 * Используется для аналитики: сколько пользователей зашли более одного раза за период.
 */
@Entity
@Table(name = "user_visits")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "visited_at", nullable = false)
    private OffsetDateTime visitedAt;

    @PrePersist
    void prePersist() {
        if (visitedAt == null) {
            visitedAt = OffsetDateTime.now();
        }
    }
}
