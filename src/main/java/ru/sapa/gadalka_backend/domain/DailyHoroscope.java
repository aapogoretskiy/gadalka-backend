package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;
import ru.sapa.gadalka_backend.domain.type.ZodiacSign;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Гороскоп на день для одного знака зодиака.
 *
 * <p>В отличие от {@link DailyCard} или {@link NumerologyDayReading}, строка здесь
 * НЕ создаётся каждый день заново на каждого пользователя — таблица всегда содержит
 * ровно 12 строк (по одной на знак), и поля {@code date}/{@code *_text} перезаписываются
 * in-place при наступлении новых суток. Так гарантируется, что на знак зодиака
 * приходится максимум один вызов AI в день, независимо от числа пользователей.
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "daily_horoscopes", uniqueConstraints = @UniqueConstraint(name = "uk_daily_horoscopes_zodiac_sign", columnNames = "zodiac_sign"))
public class DailyHoroscope {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "zodiac_sign", nullable = false, length = 20)
    private ZodiacSign zodiacSign;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "general_text", nullable = false, columnDefinition = "TEXT")
    private String generalText;

    @Column(name = "advice_text", nullable = false, columnDefinition = "TEXT")
    private String adviceText;

    @Column(name = "love_text", nullable = false, columnDefinition = "TEXT")
    private String loveText;

    @Column(name = "career_text", nullable = false, columnDefinition = "TEXT")
    private String careerText;

    @Column(name = "money_text", nullable = false, columnDefinition = "TEXT")
    private String moneyText;

    @Column(name = "general_score", nullable = false)
    private int generalScore;

    @Column(name = "love_score", nullable = false)
    private int loveScore;

    @Column(name = "career_score", nullable = false)
    private int careerScore;

    @Column(name = "money_score", nullable = false)
    private int moneyScore;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touchUpdatedAt() {
        updatedAt = OffsetDateTime.now();
    }
}
