package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "numerology_day_readings",
        uniqueConstraints = @UniqueConstraint(name = "uk_numerology_day_user_date", columnNames = {"user_id", "date"}))
public class NumerologyDayReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "day_code", nullable = false)
    private Integer dayCode;

    @Column(name = "personal_year_number", nullable = false)
    private Integer personalYearNumber;

    @Column(name = "personal_month_number", nullable = false)
    private Integer personalMonthNumber;

    @Column(nullable = false)
    private String affirmation;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;
}
