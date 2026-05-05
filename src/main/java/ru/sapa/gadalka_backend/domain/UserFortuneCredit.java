package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Текущий баланс гаданий пользователя.
 * Одна запись на пользователя. Обновляется атомарно вместе с fortune_credit_log.
 */
@Entity
@Table(name = "user_fortune_credits")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFortuneCredit {

    @Id
    @Column(name = "user_id")
    private Long userId;

    /** Текущий баланс. На уровне БД стоит CHECK (balance >= 0) */
    @Column(name = "balance", nullable = false)
    private Integer balance;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }
}
