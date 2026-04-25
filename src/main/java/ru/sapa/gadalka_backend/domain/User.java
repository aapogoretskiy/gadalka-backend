package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", nullable = false, unique = true)
    private Long telegramId;

    @Column(name = "username")
    private String username;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "fortune_used", nullable = false)
    private boolean fortuneUsed;

    @PrePersist
    void prePersist() {
        if (Objects.isNull(this.createdAt)) {
            this.createdAt = OffsetDateTime.now();
        }
    }
}
