package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "compatibility_readings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_compatibility_readings_user_id_persons_hash",
                columnNames = {"user_id", "persons_hash"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompatibilityReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * SHA-256 хеш от отсортированного списка (имя_lower:дата) участников.
     * Порядок ввода участников не влияет на хеш.
     */
    @Column(name = "persons_hash", nullable = false, length = 64)
    private String personsHash;

    /** JSON-сериализованный список PersonInput */
    @Column(name = "persons", nullable = false, columnDefinition = "TEXT")
    private String persons;

    /** Общий процент совместимости (0–100), рассчитанный нумерологически */
    @Column(name = "score", nullable = false)
    private int score;

    /** Текстовый лейбл совместимости, например «Идеальная пара» */
    @Column(name = "label", nullable = false, length = 100)
    private String label;

    /** JSON-сериализованный список CompatibilityCategoryScore */
    @Column(name = "categories", nullable = false, columnDefinition = "TEXT")
    private String categories;

    /** AI-интерпретация совместимости */
    @Column(name = "interpretation", nullable = false, columnDefinition = "TEXT")
    private String interpretation;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /**
     * Момент, когда пользователь потратил гадание на полный анализ.
     * null  — только превью (счёт + метка), интерпретация скрыта.
     * !null — разблокировано, полный анализ доступен бесплатно повторно.
     */
    @Column(name = "unlocked_at")
    private OffsetDateTime unlockedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
