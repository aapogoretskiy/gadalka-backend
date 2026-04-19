package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "fortunes",
        uniqueConstraints = @UniqueConstraint(name = "uk_fortunes_user_id_question_hash", columnNames = {"user_id", "question_hash"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Fortune {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "question_hash", nullable = false, length = 64)
    private String questionHash;

    @Column(name = "question", nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(name = "cards", nullable = false, columnDefinition = "TEXT")
    private String cards;

    @Column(name = "interpretation", nullable = false, columnDefinition = "TEXT")
    private String interpretation;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
