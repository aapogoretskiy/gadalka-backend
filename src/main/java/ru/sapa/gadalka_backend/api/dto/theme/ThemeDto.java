package ru.sapa.gadalka_backend.api.dto.theme;

import lombok.Builder;
import lombok.Getter;
import ru.sapa.gadalka_backend.domain.CardDeckTheme;

/**
 * Ответ на GET /api/themes — описание одной темы с учётом состояния пользователя.
 */
@Getter
@Builder
public class ThemeDto {

    private Long id;
    private String slug;
    private String name;
    private String description;

    /** Стоимость в кредитах. 0 для бесплатных тем. */
    private Integer price;

    /** true = пользователь уже владеет этой темой (купил или она бесплатная) */
    private boolean owned;

    /** true = это текущая активная тема пользователя */
    private boolean active;

    /** true = тема доступна для покупки. false = "скоро" */
    private boolean enabled;

    /** true = бесплатная тема (не нужно покупать) */
    private boolean free;

    public static ThemeDto from(CardDeckTheme theme, boolean owned, boolean active) {
        return ThemeDto.builder()
                .id(theme.getId())
                .slug(theme.getSlug())
                .name(theme.getName())
                .description(theme.getDescription())
                .price(theme.getPrice())
                .owned(owned)
                .active(active)
                .enabled(theme.getIsEnabled())
                .free(theme.getIsFree())
                .build();
    }
}
