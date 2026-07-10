package ru.sapa.gadalka_backend.service.interpretation;

import ru.sapa.gadalka_backend.api.dto.card.CardDto;
import ru.sapa.gadalka_backend.api.dto.compatibility.CompatibilityCategoryScore;
import ru.sapa.gadalka_backend.api.dto.compatibility.CompatibilityRequest;
import ru.sapa.gadalka_backend.domain.type.ZodiacSign;

import java.time.LocalDate;
import java.util.List;

public interface AiInterpretationService {
    InterpretationResult interpret(List<CardDto> cards, String question, String category);

    /**
     * Генерирует текстовую интерпретацию совместимости.
     * Числовые показатели рассчитываются нумерологически и передаются готовыми,
     * AI создаёт только атмосферный нарративный текст.
     */
    String interpretCompatibility(List<CompatibilityRequest.PersonInput> persons,
                                  int overallScore,
                                  List<CompatibilityCategoryScore> categories);

    /**
     * Генерирует гороскоп на день для знака зодиака.
     * Вызывается не чаще одного раза в день на знак (см. HoroscopeService) —
     * именно поэтому суммарно это не больше 12 вызовов AI в сутки.
     */
    HoroscopeContent interpretDailyHoroscope(ZodiacSign zodiacSign, LocalDate date);

    /**
     * Разбор сна (Сонник): единый LLM-вызов, возвращающий строго структурированный
     * {@link DreamContent}. Невалидный JSON приводит к ретраям внутри реализации,
     * после исчерпания попыток — {@link DreamGenerationException}.
     *
     * @param dreamText       текст сна от пользователя (может быть null/пустым, если выбраны только символы)
     * @param selectedSymbols выбранные пользователем символы-чипы с классическими значениями
     *                        (подсказки для промпта); может быть пустым списком
     * @param zodiacSign      знак зодиака пользователя (по дате рождения из профиля)
     * @param lifePathNumber  число жизни пользователя (нумерология)
     * @throws DreamRefusedException    если AI отказался разбирать сон (чувствительная тема)
     * @throws DreamGenerationException если не удалось получить валидный ответ после всех попыток
     */
    DreamContent interpretDream(String dreamText,
                                List<DreamContent.SymbolMeaning> selectedSymbols,
                                ZodiacSign zodiacSign,
                                int lifePathNumber);

    String getProvider();

    /**
     * Классифицирует вопрос по категории чувствительного контента.
     * Возвращает одно слово — название значения из {@code SensitiveContentCategory}.
     * Лёгкий вызов: ~200 входных токенов, ~10 выходных.
     */
    String classifySensitiveContent(String question);

    /**
     * Пре-чек чувствительности ДО генерации интерпретации (реальное время) и основной
     * классификатор при бэкафилле истории. В отличие от {@link #classifySensitiveContent}
     * вопрос заранее не считается чувствительным — модель сама решает, в том числе может
     * вернуть {@code NOT_SENSITIVE}.
     *
     * <p>Возвращаемое значение — сырой текст ответа модели, без гарантии формата.
     * Валидацию и ретраи при неожиданном ответе делает вызывающий код
     * ({@code SensitiveContentFilterService}), а не эта реализация.
     */
    String classifyQuestionSensitivity(String question);

    /**
     * Короткое объяснение (1-2 предложения), почему вопрос отнесён к категории —
     * только для внутреннего разбора в админке, пользователь его не видит.
     * Свободный текст, строгий формат не требуется и не валидируется.
     */
    String explainSensitiveClassification(String question, String category);
}
