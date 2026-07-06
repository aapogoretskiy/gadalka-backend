package ru.sapa.gadalka_backend.service.interpretation;

import java.util.List;

/**
 * Структурированный AI-разбор сна (фича "Сонник").
 *
 * <p>Генерируется одним LLM-вызовом в строгом JSON-формате (см. промпт в
 * {@code OpenAiCompatibleInterpretationService#interpretDream}) — по той же схеме,
 * что и {@link HoroscopeContent}: невалидный/пустой JSON приводит к ретраю,
 * а после исчерпания попыток — к {@link DreamGenerationException}.
 *
 * @param titleSymbols   2-3 ключевых символа сна для заголовка (например, ["Полёт", "Дом"]).
 *                       LLM извлекает их сама; выбранные пользователем чипы приоритетны.
 * @param mainMeaning    главный смысл сна, 3-4 предложения
 * @param lifeNumberSection трактовка в связке с числом жизни пользователя, 2-3 предложения
 * @param zodiacSection  трактовка в связке со знаком зодиака, 2-3 предложения
 * @param symbols        разбор каждого ключевого символа в контексте именно этого сна
 * @param advice         совет на сегодня по мотивам сна, 1-2 предложения
 * @param oracleQuestion готовый вопрос для кнопки «Спросить карты об этом» (переход в Оракул)
 */
public record DreamContent(
        List<String> titleSymbols,
        String mainMeaning,
        String lifeNumberSection,
        String zodiacSection,
        List<SymbolMeaning> symbols,
        String advice,
        String oracleQuestion
) {
    /** Один символ сна и его значение в контексте конкретного сна. */
    public record SymbolMeaning(String name, String meaning) {}
}
