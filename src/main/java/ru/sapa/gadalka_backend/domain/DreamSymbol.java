package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Справочник "частых символов во снах" — чипы на экране ввода Сонника.
 * Редактируется через админ-панель (см. AdminController), фронт получает
 * только активные символы через GET /api/dreams/symbols.
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "dream_symbols")
public class DreamSymbol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String emoji;

    @Column(nullable = false, length = 50)
    private String name;

    /** Классическое значение символа — подсказка для промпта AI. Может быть null. */
    @Column(name = "prompt_hint", length = 300)
    private String promptHint;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (sortOrder == null) sortOrder = 0;
        if (isActive == null) isActive = true;
    }
}
