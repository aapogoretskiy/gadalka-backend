package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Категория вопроса на экране "О чём спросить карты?" (Любовь, Деньги, Работа, Ситуация, Здоровье).
 * code — машинное имя, совпадает со значениями, разрешёнными в FortuneRequest.category.
 */
@Getter
@Setter
@Entity
@Table(name = "question_categories")
public class QuestionCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
}
