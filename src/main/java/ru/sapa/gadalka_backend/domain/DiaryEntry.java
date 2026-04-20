package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;

import java.time.OffsetDateTime;

@Entity
@Table(name = "diary_entries",
        indexes = {
                @Index(name = "idx_diary_entries_user_feature", columnList = "user_id, feature_type"),
                @Index(name = "idx_diary_entries_created_at", columnList = "created_at")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiaryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_type", nullable = false, length = 50)
    private DiaryFeatureType featureType;

    /** ID записи в исходной таблице (fortunes, compatibility_readings, daily_cards) */
    @Column(name = "reference_id")
    private Long referenceId;

    /** JSON-снимок результата работы функционала */
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
