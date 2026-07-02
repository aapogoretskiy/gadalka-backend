package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Предгенерированный вариант расклада для онбординга.
 *
 * <p>Новый пользователь получает первый расклад «в подарок» до заполнения профиля
 * и без вызова AI: вопрос выбирается из фиксированного пула (кнопки в онбординге),
 * вариант — случайный из этой таблицы. Карты настоящие (по slug из таблицы cards),
 * интерпретации написаны заранее. Пул расширяется добавлением строк без деплоя.
 */
@Entity
@Table(name = "onboarding_spreads")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingSpread {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Текст вопроса — совпадает с кнопкой в онбординге */
    @Column(name = "question", nullable = false, columnDefinition = "TEXT")
    private String question;

    /** Слаги карт через запятую ('maj01,maj07,maj21'), порядок = позиции расклада */
    @Column(name = "card_slugs", nullable = false, length = 64)
    private String cardSlugs;

    /** JSON-массив из 3 текстов по картам (прошлое, настоящее, будущее) */
    @Column(name = "per_card_interpretations", nullable = false, columnDefinition = "TEXT")
    private String perCardInterpretations;

    @Column(name = "general_interpretation", nullable = false, columnDefinition = "TEXT")
    private String generalInterpretation;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
