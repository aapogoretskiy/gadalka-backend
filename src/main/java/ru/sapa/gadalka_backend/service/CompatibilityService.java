package ru.sapa.gadalka_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.api.dto.compatibility.CompatibilityCategoryScore;
import ru.sapa.gadalka_backend.api.dto.compatibility.CompatibilityRequest;
import ru.sapa.gadalka_backend.api.dto.compatibility.CompatibilityResponse;
import ru.sapa.gadalka_backend.domain.CompatibilityReading;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;
import ru.sapa.gadalka_backend.repository.CompatibilityReadingRepository;
import ru.sapa.gadalka_backend.service.interpretation.AiInterpretationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static ru.sapa.gadalka_backend.constant.SystemConfigConstants.AI_PROVIDER;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompatibilityService {

    private final NumerologyService numerologyService;
    private final CompatibilityReadingRepository compatibilityReadingRepository;
    private final SystemConfigService systemConfigService;
    private final AiInterpretationManager interpretationManager;
    private final DiaryService diaryService;
    private final FortuneCreditService fortuneCreditService;
    private final ObjectMapper objectMapper;

    /**
     * Бесплатное превью: возвращает счёт и метку.
     * AI-интерпретация генерируется и сохраняется сразу (чтобы unlock был мгновенным),
     * но в ответ не включается пока пользователь не оплатит.
     */
    public CompatibilityResponse getCompatibility(User user, List<CompatibilityRequest.PersonInput> persons) {
        String personsHash = hashPersons(user.getId(), persons);

        // Кэш: та же пара уже анализировалась — возвращаем превью (или полный, если уже разблокирован)
        Optional<CompatibilityReading> cached = compatibilityReadingRepository.findByUserIdAndPersonsHash(user.getId(), personsHash);
        if (cached.isPresent()) {
            log.info("Возвращаем кэшированный расклад совместимости: userId={}, personsHash={}", user.getId(), personsHash);
            return buildResponse(cached.get(), persons);
        }

        // Новый расклад — генерируем AI-интерпретацию БЕЗ списания кредита
        log.info("Новый расклад совместимости для userId={}, участники: {}",
                user.getId(), persons.stream().map(CompatibilityRequest.PersonInput::getName).toList());
        NumerologyCompatibilityResult numerology = numerologyService.calculate(persons.get(0), persons.get(1));
        String label = resolveLabel(numerology.getOverallScore());

        String currentAiProvider = systemConfigService.getValue(AI_PROVIDER);
        String interpretation = interpretationManager.interpretCompatibility(
                currentAiProvider, persons, numerology.getOverallScore(), numerology.getCategories());

        CompatibilityReading saved = saveReading(user.getId(), personsHash, persons,
                numerology.getOverallScore(), label, numerology.getCategories(), interpretation);
        log.info("Расклад совместимости сохранён: readingId={}, userId={}, балл={}", saved.getId(), user.getId(), numerology.getOverallScore());

        // Сохраняем в дневник (без полного анализа — он появится после разблокировки)
        CompatibilityResponse previewResponse = buildResponse(saved, persons);
        diaryService.save(user.getId(), DiaryFeatureType.COMPATIBILITY, saved.getId(), previewResponse);
        return previewResponse;
    }

    /**
     * Разблокировка полного анализа за 1 гадание.
     * Повторный вызов для уже разблокированного расклада — бесплатен.
     */
    public CompatibilityResponse unlockCompatibility(Long readingId, User user) {
        CompatibilityReading reading = compatibilityReadingRepository
                .findByIdAndUserId(readingId, user.getId())
                .orElseThrow(() -> new RuntimeException("Расклад не найден"));

        if (reading.getUnlockedAt() == null) {
            // Ещё не разблокирован — списываем кредит
            fortuneCreditService.spendCredit(user.getId(), DiaryFeatureType.COMPATIBILITY);
            reading.setUnlockedAt(OffsetDateTime.now());
            compatibilityReadingRepository.save(reading);
            log.info("Расклад совместимости разблокирован: readingId={}, userId={}", readingId, user.getId());
        } else {
            log.info("Повторный запрос разблокированного расклада (бесплатно): readingId={}, userId={}", readingId, user.getId());
        }

        List<CompatibilityRequest.PersonInput> persons = parsePersons(reading.getPersons());
        return buildResponse(reading, persons);
    }

    // -------------------------------------------------------------------------

    private String resolveLabel(int score) {
        if (score >= 90) return "Идеальная пара";
        if (score >= 75) return "Отличная совместимость";
        if (score >= 60) return "Хорошая совместимость";
        if (score >= 45) return "Средняя совместимость";
        if (score >= 30) return "Сложный союз";
        return "Противоположности";
    }

    private CompatibilityReading saveReading(Long userId, String personsHash,
                                             List<CompatibilityRequest.PersonInput> persons,
                                             int score, String label,
                                             List<CompatibilityCategoryScore> categories,
                                             String interpretation) {
        try {
            String personsJson    = objectMapper.writeValueAsString(persons);
            String categoriesJson = objectMapper.writeValueAsString(categories);
            CompatibilityReading reading = CompatibilityReading.builder()
                    .userId(userId)
                    .personsHash(personsHash)
                    .persons(personsJson)
                    .score(score)
                    .label(label)
                    .categories(categoriesJson)
                    .interpretation(interpretation)
                    .build();
            return compatibilityReadingRepository.save(reading);
        } catch (JsonProcessingException e) {
            log.error("Ошибка сериализации данных расклада совместимости, userId={}: {}", userId, e.getMessage(), e);
            throw new IllegalStateException("Ошибка сохранения расклада совместимости", e);
        }
    }

    /** Строит ответ: превью (без интерпретации) или полный (если разблокирован). */
    private CompatibilityResponse buildResponse(CompatibilityReading reading,
                                                List<CompatibilityRequest.PersonInput> persons) {
        boolean unlocked = reading.getUnlockedAt() != null;
        try {
            List<CompatibilityCategoryScore> categories = unlocked
                    ? objectMapper.readValue(reading.getCategories(), new TypeReference<>() {})
                    : null;
            return new CompatibilityResponse(
                    persons,
                    reading.getScore(),
                    reading.getLabel(),
                    reading.getId(),
                    unlocked,
                    unlocked ? reading.getInterpretation() : null,
                    categories);
        } catch (JsonProcessingException e) {
            log.error("Ошибка десериализации расклада совместимости, readingId={}: {}", reading.getId(), e.getMessage(), e);
            throw new IllegalStateException("Ошибка чтения расклада совместимости", e);
        }
    }

    private List<CompatibilityRequest.PersonInput> parsePersons(String personsJson) {
        try {
            return objectMapper.readValue(personsJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ошибка парсинга участников расклада", e);
        }
    }

    /**
     * Хеш не зависит от порядка участников: «Иван + Мария» == «Мария + Иван».
     */
    private String hashPersons(Long userId, List<CompatibilityRequest.PersonInput> persons) {
        try {
            String normalized = persons.stream()
                    .map(p -> p.getName().trim().toLowerCase() + ":" + p.getBirthDate())
                    .sorted()
                    .collect(Collectors.joining("|"));
            String input = userId + ":" + normalized;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }
}
