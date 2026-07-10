package ru.sapa.gadalka_backend.service.interpretation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.api.dto.card.CardDto;
import ru.sapa.gadalka_backend.api.dto.compatibility.CompatibilityCategoryScore;
import ru.sapa.gadalka_backend.api.dto.compatibility.CompatibilityRequest;
import ru.sapa.gadalka_backend.domain.type.ZodiacSign;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiInterpretationManager {
    private final Map<String, AiInterpretationService> strategies;

    public InterpretationResult interpret(String provider, List<CardDto> cards, String question, String category) {
        log.debug("Интерпретация расклада таро через провайдер '{}', карт: {}, категория: {}", provider, cards.size(), category);
        return getService(provider).interpret(cards, question, category);
    }

    public String interpretCompatibility(String provider,
                                         List<CompatibilityRequest.PersonInput> persons,
                                         int overallScore,
                                         List<CompatibilityCategoryScore> categories) {
        log.debug("Интерпретация совместимости через провайдер '{}', участников: {}", provider, persons.size());
        return getService(provider).interpretCompatibility(persons, overallScore, categories);
    }

    public HoroscopeContent interpretDailyHoroscope(String provider, ZodiacSign zodiacSign, LocalDate date) {
        log.debug("Генерация гороскопа на день через провайдер '{}', знак: {}, дата: {}", provider, zodiacSign, date);
        return getService(provider).interpretDailyHoroscope(zodiacSign, date);
    }

    public DreamContent interpretDream(String provider,
                                       String dreamText,
                                       List<DreamContent.SymbolMeaning> selectedSymbols,
                                       ZodiacSign zodiacSign,
                                       int lifePathNumber) {
        log.debug("Разбор сна через провайдер '{}', символов: {}, длина текста: {}",
                provider, selectedSymbols.size(), dreamText != null ? dreamText.length() : 0);
        return getService(provider).interpretDream(dreamText, selectedSymbols, zodiacSign, lifePathNumber);
    }

    public String classifySensitiveContent(String provider, String question) {
        log.debug("Классификация чувствительного контента через провайдер '{}'", provider);
        return getService(provider).classifySensitiveContent(question);
    }

    public String classifyQuestionSensitivity(String provider, String question) {
        log.debug("Пре-чек чувствительности вопроса через провайдер '{}'", provider);
        return getService(provider).classifyQuestionSensitivity(question);
    }

    public String explainSensitiveClassification(String provider, String question, String category) {
        log.debug("Запрос объяснения категории '{}' через провайдер '{}'", category, provider);
        return getService(provider).explainSensitiveClassification(question, category);
    }

    private AiInterpretationService getService(String provider) {
        AiInterpretationService service = strategies.get(provider);
        if (service == null) {
            log.error("Неизвестный AI-провайдер: '{}'. Доступные провайдеры: {}", provider, strategies.keySet());
            throw new IllegalArgumentException("Неизвестный AI-провайдер: " + provider);
        }
        return service;
    }
}
