package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Заготовленный вопрос-подсказка для конкретной категории.
 * Пользователь может выбрать его готовым на экране "О чём спросить карты?",
 * либо ввести свой вопрос — на обработку гадания (/api/fortune) это не влияет.
 */
@Getter
@Setter
@Entity
@Table(name = "question_presets")
public class QuestionPreset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private QuestionCategory category;

    @Column(name = "question_text", nullable = false, length = 300)
    private String questionText;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
}
