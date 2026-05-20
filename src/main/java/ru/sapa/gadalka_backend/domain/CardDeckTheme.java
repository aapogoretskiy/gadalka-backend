package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Тема (колода) карт Таро.
 * Определяет внешний вид карт: у каждой темы своя папка картинок в CDN.
 */
@Getter
@Setter
@Entity
@Table(name = "card_deck_themes")
public class CardDeckTheme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String slug;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Корневой URL папки с картинками в CDN.
     * Итоговый URL карты: base_url + card.slug + ".jpg"
     * Например: "https://cdn.magicliora.com/themes/classic/" + "the-fool" + ".jpg"
     */
    @Column(name = "base_url", length = 512)
    private String baseUrl;

    /**
     * Расширение файлов картинок: "jpg", "png", "webp" и т.д.
     * Итоговый URL = baseUrl + card.slug + "." + imageExtension
     */
    @Column(name = "image_extension", nullable = false, length = 10)
    private String imageExtension;

    @Column(nullable = false)
    private Integer price;

    @Column(name = "is_free", nullable = false)
    private Boolean isFree;

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
