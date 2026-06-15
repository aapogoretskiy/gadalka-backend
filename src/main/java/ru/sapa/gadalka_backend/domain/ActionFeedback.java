package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;
import ru.sapa.gadalka_backend.domain.type.FeedbackRating;
import ru.sapa.gadalka_backend.domain.type.FeedbackTargetType;

import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "action_feedbacks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActionFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * Тип действия, на которое оставлен фидбэк.
     * Хранится как строка для удобства чтения в БД и расширяемости.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 50)
    private FeedbackTargetType actionType;

    /** ID записи в соответствующей таблице (fortunes.id, compatibility_readings.id и т.д.) */
    @Column(name = "action_id", nullable = false)
    private Long actionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rating", nullable = false, length = 10)
    private FeedbackRating rating;

    /** Опциональный комментарий — заполняется только при NEGATIVE */
    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (Objects.isNull(this.createdAt)) {
            this.createdAt = OffsetDateTime.now();
        }
    }
}
