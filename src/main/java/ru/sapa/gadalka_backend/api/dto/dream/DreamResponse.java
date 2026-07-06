package ru.sapa.gadalka_backend.api.dto.dream;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Полный разбор сна для экрана результата. Этот же объект сериализуется
 * в {@code dream_readings.payload} и в дневник — открытие из истории
 * отдаёт его без повторного обращения к AI.
 *
 * @param titleSymbols    2-3 ключевых символа для заголовка («Полёт · Дом · Незнакомец»)
 * @param lifeNumber      число жизни пользователя (для заголовка секции)
 * @param lifeNumberTitle архетип числа («Лидер» и т.п. — как в нумерологии)
 * @param zodiacSign      отображаемое имя знака («Телец») для заголовка секции
 * @param symbols         разбор символов сна — включая выбранные пользователем чипы
 * @param oracleQuestion  предзаполненный вопрос для кнопки «Спросить карты об этом»
 */
public record DreamResponse(
        Long id,
        OffsetDateTime createdAt,
        String dreamText,
        List<String> selectedSymbols,
        List<String> titleSymbols,
        String mainMeaning,
        int lifeNumber,
        String lifeNumberTitle,
        String lifeNumberSection,
        String zodiacSign,
        String zodiacSection,
        List<DreamSymbolMeaningDto> symbols,
        String advice,
        String oracleQuestion
) {
    /** Один символ сна и его трактовка в контексте конкретного сна. */
    public record DreamSymbolMeaningDto(String name, String meaning) {}
}
