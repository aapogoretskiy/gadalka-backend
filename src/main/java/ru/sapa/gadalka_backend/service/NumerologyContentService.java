package ru.sapa.gadalka_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.api.dto.numerology.NumerologyMonthLifeAreaDto;
import ru.sapa.gadalka_backend.api.dto.numerology.NumerologyMonthLifeAreasDto;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class NumerologyContentService {

    private static final String DATA_NUMEROLOGY_CONTENT_PATH = "/data/numerology-content.json";

    private final ObjectMapper objectMapper;

    private JsonNode numbersNode;
    private JsonNode monthlyAstroNode;

    @PostConstruct
    void load() {
        try {
            JsonNode root = objectMapper.readTree(new ClassPathResource(DATA_NUMEROLOGY_CONTENT_PATH).getInputStream());
            numbersNode = root.get("numbers");
            monthlyAstroNode = root.get("monthlyAstroEvents");
            log.info("Numerology content loaded successfully");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load numerology-content.json", e);
        }
    }

    public String title(int dayCode) {
        return numberNode(dayCode).get("title").asText();
    }

    public String bestTime(int dayCode) {
        return numberNode(dayCode).get("bestTime").asText();
    }

    public String energyOfDay(int dayCode) {
        return numberNode(dayCode).get("energy").asText();
    }

    public String whatToDo(int dayCode) {
        return numberNode(dayCode).get("whatToDo").asText();
    }

    public String whatToAvoid(int dayCode) {
        return numberNode(dayCode).get("whatToAvoid").asText();
    }

    public String randomAffirmation(int dayCode) {
        JsonNode affs = numberNode(dayCode).get("affirmations");
        int idx = ThreadLocalRandom.current().nextInt(affs.size());
        return affs.get(idx).asText();
    }

    /** Название числа жизненного пути — постоянная характеристика (Гуманист, Лидер и т.д.) */
    public String lifePathTitle(int lifePathNumber) {
        return numberNode(lifePathNumber).get("lifePathTitle").asText();
    }

    /** Описание числа жизненного пути — общая характеристика числа. */
    public String lifePathDescription(int lifePathNumber) {
        return energyOfDay(lifePathNumber);
    }

    // ── Портрет личности ──────────────────────────────────────────────────────

    /** Полное описание числа жизни для портрета (2-3 предложения). */
    public String portraitLifePathDescription(int number) {
        return textOrNull(numberNode(number), "lifePathDescription");
    }

    /** Короткое описание числа для карточек числа души / имени / дня рождения. */
    public String portraitShortDescription(int number) {
        return textOrNull(numberNode(number), "shortDescription");
    }

    public String portraitStrengths(int number) {
        return textOrNull(numberNode(number), "strengths");
    }

    public String portraitGrowthPoints(int number) {
        return textOrNull(numberNode(number), "growthPoints");
    }

    public String portraitCalling(int number) {
        return textOrNull(numberNode(number), "calling");
    }

    public String portraitFamousPeople(int number) {
        return textOrNull(numberNode(number), "famousPeople");
    }

    public String monthlyAstroEvent(int month) {
        JsonNode node = monthlyAstroNode.get(String.valueOf(month));
        return node != null ? node.asText() : "";
    }

    // ── Контент недельного расклада (по числу недели) ──────────────────────────

    public String weekMainTheme(int weekNumber) {
        return textOrNull(numberNode(weekNumber), "weekMainTheme");
    }

    public String weekWhatToStrengthen(int weekNumber) {
        return textOrNull(numberNode(weekNumber), "weekWhatToStrengthen");
    }

    public String weekWhatToAvoid(int weekNumber) {
        return textOrNull(numberNode(weekNumber), "weekWhatToAvoid");
    }

    public String weekRelationships(int weekNumber) {
        return textOrNull(numberNode(weekNumber), "weekRelationships");
    }

    public String weekFinance(int weekNumber) {
        return textOrNull(numberNode(weekNumber), "weekFinance");
    }

    /** Короткая фраза-совет для пикового дня недели (по коду конкретного дня). */
    public String peakAdvice(int dayCode) {
        return textOrNull(numberNode(dayCode), "peakAdvice");
    }

    // ── Контент месячного разбора (по числу месяца) ─────────────────────────────
    // Тексты в JSON содержат плейсхолдеры {Month}/{month}/{monthPrep}, которые здесь
    // подставляются под конкретный календарный месяц (1-12) — один и тот же архетип
    // числа должен звучать корректно и для июля, и для декабря.

    private static final String[] MONTHS_NOMINATIVE = {
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
    };
    private static final String[] MONTHS_PREPOSITIONAL = {
        "январе", "феврале", "марте", "апреле", "мае", "июне",
        "июле", "августе", "сентябре", "октябре", "ноябре", "декабре"
    };

    public String monthNameNominative(int calendarMonth) {
        return MONTHS_NOMINATIVE[calendarMonth - 1];
    }

    public String monthMainTheme(int monthNumber, int calendarMonth) {
        return applyMonthPlaceholders(textOrNull(numberNode(monthNumber), "monthMainTheme"), calendarMonth);
    }

    public String monthWhatToAvoid(int monthNumber, int calendarMonth) {
        return applyMonthPlaceholders(textOrNull(numberNode(monthNumber), "monthWhatToAvoid"), calendarMonth);
    }

    public String monthAdvice(int monthNumber, int calendarMonth) {
        return applyMonthPlaceholders(textOrNull(numberNode(monthNumber), "monthAdvice"), calendarMonth);
    }

    public NumerologyMonthLifeAreasDto monthLifeAreas(int monthNumber, int calendarMonth) {
        JsonNode areas = numberNode(monthNumber).get("monthLifeAreas");
        return new NumerologyMonthLifeAreasDto(
                monthLifeArea(areas, "relationships", calendarMonth),
                monthLifeArea(areas, "career", calendarMonth),
                monthLifeArea(areas, "finance", calendarMonth),
                monthLifeArea(areas, "health", calendarMonth)
        );
    }

    private NumerologyMonthLifeAreaDto monthLifeArea(JsonNode areas, String key, int calendarMonth) {
        JsonNode node = areas.get(key);
        return new NumerologyMonthLifeAreaDto(
                node.get("score").asInt(),
                applyMonthPlaceholders(node.get("text").asText(), calendarMonth)
        );
    }

    private String applyMonthPlaceholders(String text, int calendarMonth) {
        if (text == null) return null;
        return text
                .replace("{Month}", monthNameNominative(calendarMonth))
                .replace("{month}", monthNameNominative(calendarMonth).toLowerCase())
                .replace("{monthPrep}", MONTHS_PREPOSITIONAL[calendarMonth - 1]);
    }

    // ── Контент годового разбора (по числу года) ─────────────────────────────────
    // В отличие от месяца, год не требует падежных форм — просто подставляем
    // календарный год числом ({Year} → "2026").

    public String yearTitle(int yearNumber) {
        return textOrNull(numberNode(yearNumber), "yearTitle");
    }

    public String yearMainTheme(int yearNumber, int calendarYear) {
        return applyYearPlaceholders(textOrNull(numberNode(yearNumber), "yearMainTheme"), calendarYear);
    }

    public String yearWhatToAvoid(int yearNumber, int calendarYear) {
        return applyYearPlaceholders(textOrNull(numberNode(yearNumber), "yearWhatToAvoid"), calendarYear);
    }

    public String yearAdvice(int yearNumber, int calendarYear) {
        return applyYearPlaceholders(textOrNull(numberNode(yearNumber), "yearAdvice"), calendarYear);
    }

    public NumerologyMonthLifeAreasDto yearLifeAreas(int yearNumber, int calendarYear) {
        JsonNode areas = numberNode(yearNumber).get("yearLifeAreas");
        return new NumerologyMonthLifeAreasDto(
                yearLifeArea(areas, "relationships", calendarYear),
                yearLifeArea(areas, "career", calendarYear),
                yearLifeArea(areas, "finance", calendarYear),
                yearLifeArea(areas, "health", calendarYear)
        );
    }

    private NumerologyMonthLifeAreaDto yearLifeArea(JsonNode areas, String key, int calendarYear) {
        JsonNode node = areas.get(key);
        return new NumerologyMonthLifeAreaDto(
                node.get("score").asInt(),
                applyYearPlaceholders(node.get("text").asText(), calendarYear)
        );
    }

    /**
     * Короткий совет под один из 4 ключевых периодов года (Старт/Пауза/Пик/Итоги) — по числу
     * МЕСЯЦА, который выиграл этот период (не по числу года!), см. NumerologyYearService.
     */
    public String yearPeriodAdvice(int monthNumber, String periodKey) {
        JsonNode node = numberNode(monthNumber).get("yearPeriodAdvice");
        return node != null ? textOrNull(node, periodKey) : null;
    }

    private String applyYearPlaceholders(String text, int calendarYear) {
        if (text == null) return null;
        return text.replace("{Year}", String.valueOf(calendarYear));
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null ? value.asText() : null;
    }

    private JsonNode numberNode(int dayCode) {
        JsonNode node = numbersNode.get(String.valueOf(dayCode));
        if (node == null) {
            node = numbersNode.get("7");
        }
        return node;
    }
}
