package ru.sapa.gadalka_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.sapa.gadalka_backend.api.dto.card.DailyCardResponse;
import ru.sapa.gadalka_backend.api.dto.diary.DiaryEntryDto;
import ru.sapa.gadalka_backend.api.dto.diary.DiaryHistoryResponse;
import ru.sapa.gadalka_backend.domain.CompatibilityReading;
import ru.sapa.gadalka_backend.domain.DailyCard;
import ru.sapa.gadalka_backend.domain.DiaryEntry;
import ru.sapa.gadalka_backend.domain.Fortune;
import ru.sapa.gadalka_backend.domain.NumerologyDayReading;
import ru.sapa.gadalka_backend.domain.NumerologyWeekReading;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;
import ru.sapa.gadalka_backend.mapper.CardMapper;
import ru.sapa.gadalka_backend.repository.CompatibilityReadingRepository;
import ru.sapa.gadalka_backend.repository.DailyCardRepository;
import ru.sapa.gadalka_backend.repository.DiaryRepository;
import ru.sapa.gadalka_backend.repository.FortuneRepository;
import ru.sapa.gadalka_backend.repository.NumerologyDayReadingRepository;
import ru.sapa.gadalka_backend.repository.NumerologyWeekReadingRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryService {

    private final DiaryRepository diaryRepository;
    private final FortuneRepository fortuneRepository;
    private final CompatibilityReadingRepository compatibilityReadingRepository;
    private final DailyCardRepository dailyCardRepository;
    private final NumerologyDayReadingRepository numerologyDayReadingRepository;
    private final NumerologyWeekReadingRepository numerologyWeekReadingRepository;
    private final CardMapper cardMapper;
    private final ObjectMapper objectMapper;

    /**
     * Сохраняет запись в дневник автоматически при создании нового результата в сервисах-источниках.
     */
    public void save(Long userId, DiaryFeatureType featureType, Long referenceId, Object payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            DiaryEntry entry = DiaryEntry.builder()
                    .userId(userId)
                    .featureType(featureType)
                    .referenceId(referenceId)
                    .payload(payloadJson)
                    .build();
            diaryRepository.save(entry);
            log.debug("Diary entry saved: userId={} featureType={} referenceId={}", userId, featureType, referenceId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize diary payload for userId={} featureType={}", userId, featureType, e);
        }
    }

    /**
     * Сохраняет запись дневника вручную по ссылке на существующий результат.
     * Проверяет принадлежность записи текущему пользователю.
     */
    public DiaryEntryDto saveByReference(User user, DiaryFeatureType featureType, Long referenceId) {
        Object payload = resolvePayload(user, featureType, referenceId);
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            DiaryEntry entry = DiaryEntry.builder()
                    .userId(user.getId())
                    .featureType(featureType)
                    .referenceId(referenceId)
                    .payload(payloadJson)
                    .build();
            DiaryEntry saved = diaryRepository.save(entry);
            log.debug("Diary entry manually saved: userId={} featureType={} referenceId={}", user.getId(), featureType, referenceId);
            return new DiaryEntryDto(saved.getId(), saved.getFeatureType(), saved.getCreatedAt(), parsePayload(payloadJson));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize diary payload for userId={} featureType={}", user.getId(), featureType, e);
            throw new IllegalStateException("Ошибка сохранения записи в дневник", e);
        }
    }

    /**
     * Возвращает историю пользователя по типу функционала в заданном диапазоне дат (включительно).
     */
    public DiaryHistoryResponse getHistory(Long userId,
                                           DiaryFeatureType featureType,
                                           LocalDate from,
                                           LocalDate to) {
        OffsetDateTime fromDt = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime toDt   = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        List<DiaryEntry> entries = diaryRepository.findByUserIdAndFeatureTypeAndCreatedAtBetweenOrderByCreatedAtDesc(userId, featureType, fromDt, toDt);

        List<DiaryEntryDto> dtos = entries.stream()
                .map(e -> {
                    JsonNode data;
                    if (e.getFeatureType() == DiaryFeatureType.COMPATIBILITY && e.getReferenceId() != null) {
                        data = compatibilityReadingRepository.findById(e.getReferenceId())
                                .map(r -> {
                                    try {
                                        return (JsonNode) objectMapper.valueToTree(resolveCompatibilityPayload(userId, r.getId()));
                                    } catch (Exception ex) {
                                        log.warn("Не удалось загрузить живые данные совместимости для diaryEntryId={}, используем снимок", e.getId());
                                        return parsePayload(e.getPayload());
                                    }
                                })
                                .orElseGet(() -> parsePayload(e.getPayload()));
                    } else {
                        data = parsePayload(e.getPayload());
                    }
                    return new DiaryEntryDto(e.getId(), e.getFeatureType(), e.getCreatedAt(), data);
                })
                .toList();

        return new DiaryHistoryResponse(dtos);
    }

    // -------------------------------------------------------------------------

    private Object resolvePayload(User user, DiaryFeatureType featureType, Long referenceId) {
        return switch (featureType) {
            case THREE_CARD, HORSESHOE, CELTIC_CROSS -> resolveFortunePayload(user.getId(), referenceId);
            case COMPATIBILITY    -> resolveCompatibilityPayload(user.getId(), referenceId);
            case DAILY_CARD       -> resolveDailyCardPayload(user.getId(), referenceId);
            case NUMEROLOGY_DAY   -> resolveNumerologyDayPayload(user.getId(), referenceId);
            case NUMEROLOGY_WEEK  -> resolveNumerologyWeekPayload(user.getId(), referenceId);
            case NUMEROLOGY_MONTH -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Месячный разбор сохраняется в дневник автоматически");
            case DAILY_HOROSCOPE  -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Гороскоп на день сохраняется в дневник автоматически");
            case NUMEROLOGY_PORTRAIT -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Портрет личности сохраняется в дневник автоматически");
            case DREAM -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Результат работы сна сохраняется в дневник автоматически");
        };
    }

    private Object resolveFortunePayload(Long userId, Long referenceId) {
        Fortune fortune = fortuneRepository.findById(referenceId)
                .filter(f -> f.getUserId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Гадание не найдено"));
        try {
            return Map.of(
                    "question", fortune.getQuestion(),
                    "cards", objectMapper.readTree(fortune.getCards()),
                    "interpretation", fortune.getInterpretation()
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ошибка чтения данных гадания", e);
        }
    }

    private Object resolveCompatibilityPayload(Long userId, Long referenceId) {
        CompatibilityReading reading = compatibilityReadingRepository.findById(referenceId)
                .filter(r -> r.getUserId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Расклад совместимости не найден"));
        boolean unlocked = reading.getUnlockedAt() != null;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", reading.getId());
            payload.put("persons", objectMapper.readTree(reading.getPersons()));
            payload.put("compatibilityScore", reading.getScore());
            payload.put("label", reading.getLabel());
            payload.put("unlocked", unlocked);
            payload.put("interpretation", unlocked ? reading.getInterpretation() : null);
            payload.put("categories", unlocked ? objectMapper.readTree(reading.getCategories()) : null);
            return payload;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ошибка чтения данных совместимости", e);
        }
    }

    private DailyCardResponse resolveDailyCardPayload(Long userId, Long referenceId) {
        DailyCard dailyCard = dailyCardRepository.findByIdAndUserId(referenceId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Карта дня не найдена"));
        return cardMapper.toDailyCardDto(dailyCard, null);
    }

    private JsonNode resolveNumerologyDayPayload(Long userId, Long referenceId) {
        NumerologyDayReading reading = numerologyDayReadingRepository.findByIdAndUserId(referenceId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Нумерологический расчёт не найден"));
        return parsePayload(reading.getPayload());
    }

    private JsonNode resolveNumerologyWeekPayload(Long userId, Long referenceId) {
        NumerologyWeekReading reading = numerologyWeekReadingRepository.findByIdAndUserId(referenceId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Расклад на неделю не найден"));
        return parsePayload(reading.getPayload());
    }

    private JsonNode parsePayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse diary entry payload", e);
            return objectMapper.nullNode();
        }
    }
}
