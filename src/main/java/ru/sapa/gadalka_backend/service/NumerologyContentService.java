package ru.sapa.gadalka_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

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
