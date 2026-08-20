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

    /**
     * Была ли реально заблокирована выдача пользователю.
     *
     * <p>До появления этого поля запись в таблице и блокировка означали одно и то же.
     * Теперь нет: уровень 3 (отказ генерирующей модели) логирует случай, но НЕ блокирует
     * пользователя, если классификатор не подтвердил реальную запрещённую категорию —
     * такие записи нужны для настройки промптов, а не как метрика отказов.
     *
     * <p>Записи бэкафилла всегда {@code false}: это разметка истории постфактум,
     * пользователь свой ответ в тот момент получил.
     *
     * <p>{@code @Builder.Default} обязателен: без него Lombok игнорирует инициализатор
     * поля, и через билдер сюда пришёл бы {@code false} вместо {@code true}.
     */
    @Builder.Default
    @Column(name = "blocked", nullable = false)
    private boolean blocked = true;

    @Column(name = "detected_at", nullable = false)
    private OffsetDateTime detectedAt;

    @PrePersist
    public void prePersist() {
        if (detectedAt == null) {
            detectedAt = OffsetDateTime.now();
        }
    }
}
