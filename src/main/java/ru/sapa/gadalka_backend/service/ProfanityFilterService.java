package ru.sapa.gadalka_backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Фильтр нецензурной лексики для входящих запросов на гадание.
 * При обнаружении мата выбрасывает IllegalArgumentException с мистическим ответом.
 */
@Slf4j
@Service
public class ProfanityFilterService {

    private static final String REJECTION_MESSAGE =
            "Карты и Вселенная требуют уважения 🌙 Пожалуйста, перефразируйте свой вопрос";

    // Нормализованные (ё→е) базовые корни нецензурных слов — без дублей
    private static final Set<String> PROFANITY_ROOTS = Set.of(
            "хуй", "хую", "хуя", "хуев", "хуйн", "хует", "хуил", "хуяр",
            "пизд", "пизж", "пиздеж",
            "блять", "бляд",
            "ебат", "ебал", "ебан", "еблан", "ебло", "ебну", "ебот", "ебут",
            "муда", "мудил",
            "залуп",
            "манда",
            "уебок", "уебищ",
            "долбоеб",
            "трахат", "трахну",
            "сучар", "шлюх",
            "ублюд"
    );

    private static final Pattern NORMALIZE_PATTERN = Pattern.compile("[^а-яa-z0-9]");

    /**
     * Проверяет текст на наличие нецензурной лексики.
     *
     * @param text входящий текст запроса
     * @throws IllegalArgumentException если обнаружена нецензурная лексика
     */
    public void validate(String text) {
        if (text == null || text.isBlank()) return;

        String normalized = normalize(text);

        for (String root : PROFANITY_ROOTS) {
            if (normalized.contains(root)) {
                log.warn("Обнаружена нецензурная лексика в запросе (корень: '{}')", root);
                throw new IllegalArgumentException(REJECTION_MESSAGE);
            }
        }
    }

    private String normalize(String text) {
        return NORMALIZE_PATTERN
                .matcher(text.toLowerCase().replace('ё', 'е'))
                .replaceAll("");
    }
}
