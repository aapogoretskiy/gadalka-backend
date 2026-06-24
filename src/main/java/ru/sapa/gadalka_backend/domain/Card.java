package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ru.sapa.gadalka_backend.domain.type.ArcanaType;
import ru.sapa.gadalka_backend.domain.type.Rank;
import ru.sapa.gadalka_backend.domain.type.Suit;

@Getter
@Setter
@Entity
@Table(name = "cards")
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "arcana_type", nullable = false)
    private ArcanaType arcanaType;

    @Enumerated(EnumType.STRING)
    private Suit suit;

    @Enumerated(EnumType.STRING)
    private Rank rank;

    private String meaning;

    @Column(columnDefinition = "TEXT")
    private String advice;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "slug", length = 100)
    private String slug;

    @Column(name = "insight_title")
    private String insightTitle;

    @Column(name = "description_paragraph_1", columnDefinition = "TEXT")
    private String descriptionParagraph1;

    @Column(name = "description_paragraph_2", columnDefinition = "TEXT")
    private String descriptionParagraph2;

    /**
     * Ключевые слова карты, хранятся как строка через запятую (например: "НАЧАЛО, ПОТЕНЦИАЛ, ИМПУЛЬС").
     * В DTO разбиваются на List<String> мапппером.
     */
    @Column(name = "keywords")
    private String keywords;
}
