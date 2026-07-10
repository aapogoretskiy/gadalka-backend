package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;
import ru.sapa.gadalka_backend.domain.type.DetectionSource;
import ru.sapa.gadalka_backend.domain.type.SensitiveContentCategory;

import java.time.OffsetDateTime;

@Entity
@Table(name = "sensitive_query_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensitiveQueryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "question", nullable = false)
    private String question;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 100)
    private SensitiveContentCategory category;

    /** Каким механизмом обнаружено — keyword / LLM pre-check / отказ LLM / бэкафилл */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 50)
    private DetectionSource source;

    /**
     * Сырой ответ LLM, когда он не совпал ни с одним ожидаемым словом
     * (см. {@link SensitiveContentCategory#CLASSIFICATION_FAILED}). Заполняется
     * только в этом случае — нужно для отладки промпта, не для машинной обработки.
     */
    @Column(name = "raw_classification_output", columnDefinition = "TEXT")
    private String rawClassificationOutput;

    /**
     * "Почему заблокировано" для админки: для keyword-источника — сработавший корень
     * (без вызова LLM, это уже детерминировано), для LLM-источника — короткое
     * пояснение от модели, дозаполняется асинхронно после логирования записи.
     */
    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "detected_at", nullable = false)
    private OffsetDateTime detectedAt;

    @PrePersist
    public void prePersist() {
        if (detectedAt == null) {
            detectedAt = OffsetDateTime.now();
        }
    }
}
