package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Разбор сна (Сонник). Полный ответ AI хранится снимком в {@code payload} (JSON) —
 * как в {@link NumerologyWeekReading}: генерация платная и происходит один раз,
 * повторное открытие из истории бесплатно и не зависит от доступности AI.
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "dream_readings")
public class DreamReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Текст сна от пользователя. Null, если выбраны только символы-чипы. */
    @Column(name = "dream_text", length = 1000)
    private String dreamText;

    /** Снимок выбранных символов на момент разбора — JSON-массив строк. */
    @Column(name = "selected_symbols", columnDefinition = "TEXT")
    private String selectedSymbols;

    /** Полный ответ AI (DreamResponse) в JSON. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
