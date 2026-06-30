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

    String getProvider();

    /**
     * Классифицирует вопрос по категории чувствительного контента.
     * Возвращает одно слово — название значения из {@code SensitiveContentCategory}.
     * Лёгкий вызов: ~200 входных токенов, ~10 выходных.
     */
    String classifySensitiveContent(String question);
}
