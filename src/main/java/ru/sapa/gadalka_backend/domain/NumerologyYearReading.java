package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "numerology_year_readings",
        uniqueConstraints = @UniqueConstraint(name = "uk_numerology_year_user_start", columnNames = {"user_id", "year_start_date"}))
public class NumerologyYearReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "year_start_date", nullable = false)
    private LocalDate yearStartDate;

    @Column(name = "year_end_date", nullable = false)
    private LocalDate yearEndDate;

    @Column(name = "year_number", nullable = false)
    private Integer yearNumber;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
