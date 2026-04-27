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

    private final ObjectMapper objectMapper;

    private JsonNode numbersNode;
    private JsonNode monthlyAstroNode;

    @PostConstruct
    void load() {
        try {
            JsonNode root = objectMapper.readTree(new ClassPathResource("numerology-content.json").getInputStream());
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

    public String monthlyAstroEvent(int month) {
        JsonNode node = monthlyAstroNode.get(String.valueOf(month));
        return node != null ? node.asText() : "";
    }

    private JsonNode numberNode(int dayCode) {
        JsonNode node = numbersNode.get(String.valueOf(dayCode));
        if (node == null) {
            node = numbersNode.get("7");
        }
        return node;
    }
}
