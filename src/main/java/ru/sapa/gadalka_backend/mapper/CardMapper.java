package ru.sapa.gadalka_backend.mapper;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import ru.sapa.gadalka_backend.api.dto.card.CardDto;
import ru.sapa.gadalka_backend.api.dto.card.DailyCardResponse;
import ru.sapa.gadalka_backend.domain.Card;
import ru.sapa.gadalka_backend.domain.CardDeckTheme;
import ru.sapa.gadalka_backend.domain.DailyCard;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Component
public class CardMapper {

    public CardDto toDto(Card card) {
        return toDto(card, null);
    }

    /**
     * Конвертирует Card в CardDto с учётом активной темы пользователя.
     *
     * @param card  сущность карты
     * @param theme активная тема (может быть null — тогда imageUrl берётся из card.imageUrl)
     */
    public CardDto toDto(Card card, CardDeckTheme theme) {
        return CardDto.builder()
                .id(card.getId())
                .name(card.getName())
                .meaning(card.getMeaning())
                .imageUrl(resolveImageUrl(card, theme))
                .build();
    }

    /**
     * Конвертирует DailyCard в ответ для фронта.
     *
     * @param dailyCard запись карты дня
     * @param theme     активная тема пользователя (может быть null — тогда используем card.imageUrl)
     */
    public DailyCardResponse toDailyCardDto(DailyCard dailyCard, CardDeckTheme theme) {
        Card card = dailyCard.getCard();
        if (Objects.isNull(card)) {
            throw new RuntimeException(String.format("Cannot find card in daily card model by id: %s and for user id: %s",
                    dailyCard.getId(), dailyCard.getUserId()));
        }
        return new DailyCardResponse(dailyCard.getId(),
                card.getName(),
                card.getMeaning(),
                card.getAdvice(),
                resolveImageUrl(card, theme),
                dailyCard.getDate(),
                card.getInsightTitle(),
                card.getDescriptionParagraph1(),
                card.getDescriptionParagraph2(),
                parseKeywords(card.getKeywords()));
    }

    /**
     * Разбивает строку ключевых слов карты ("НАЧАЛО, ПОТЕНЦИАЛ, ИМПУЛЬС") на список.
     * Возвращает пустой список, если у карты пока нет ключевых слов (например, старые данные).
     */
    private List<String> parseKeywords(String keywords) {
        if (!StringUtils.hasText(keywords)) {
            return Collections.emptyList();
        }
        return Arrays.stream(keywords.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    /**
     * Определяет URL картинки карты с учётом активной темы.
     * Логика:
     * 1. Если у темы задан base_url И у карты есть slug →
     *    собираем URL: base_url + card.slug + "." + imageExtension
     *    Пример: "<a href="https://cdn.magicliora.com/themes/cosmic/">...</a>" + "the-fool" + ".webp"
     * 2. Иначе → возвращаем card.imageUrl (классика или null, если картинок ещё нет)
     * Расширение берётся из темы (jpg/png/webp и т.д.) — каждая тема может
     * использовать свой формат. Метод public — используется и в других сервисах.
     */
    public String resolveImageUrl(Card card, CardDeckTheme theme) {
        if (theme != null
                && theme.getBaseUrl() != null
                && card.getSlug() != null) {
            String ext = theme.getImageExtension() != null ? theme.getImageExtension() : "jpg";
            return theme.getBaseUrl() + card.getSlug() + "." + ext;
        }
        return card.getImageUrl();
    }
}
