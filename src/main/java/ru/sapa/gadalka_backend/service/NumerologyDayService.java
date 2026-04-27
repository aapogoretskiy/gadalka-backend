package ru.sapa.gadalka_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.sapa.gadalka_backend.api.dto.numerology.NumerologyDayResponse;
import ru.sapa.gadalka_backend.domain.NumerologyDayReading;
import ru.sapa.gadalka_backend.domain.UserProfile;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;
import ru.sapa.gadalka_backend.repository.NumerologyDayReadingRepository;
import ru.sapa.gadalka_backend.repository.UserProfileRepository;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class NumerologyDayService {

    private final NumerologyService numerologyService;
    private final NumerologyContentService contentService;
    private final NumerologyDayReadingRepository repository;
    private final UserProfileRepository userProfileRepository;
    private final DiaryService diaryService;
    private final ObjectMapper objectMapper;

    @Transactional
    public NumerologyDayResponse getToday(Long userId) {
        LocalDate today = LocalDate.now();

        return repository.findByUserIdAndDate(userId, today)
                .map(this::toResponse)
                .orElseGet(() -> createAndSave(userId, today));
    }

    private NumerologyDayResponse createAndSave(Long userId, LocalDate today) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Для расчёта нумерологии необходимо указать дату рождения в профиле"));

        if (profile.getBirthDate() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Для расчёта нумерологии необходимо указать дату рождения в профиле");
        }

        LocalDate birthDate = profile.getBirthDate();

        int dayCode = numerologyService.personalDayCode(birthDate, today);
        int lifePathNum = numerologyService.lifePathNumber(birthDate);
        int personalYearNum = numerologyService.personalYearNumber(birthDate, today.getYear());
        int personalMonthNum = numerologyService.personalMonthNumber(birthDate, today.getYear(), today.getMonthValue());
        String affirmation = contentService.randomAffirmation(dayCode);

        NumerologyDayResponse response = new NumerologyDayResponse(
                null,
                today,
                dayCode,
                contentService.title(dayCode),
                lifePathNum,
                contentService.lifePathTitle(lifePathNum),
                numerologyService.moonPhase(today),
                numerologyService.zodiacSign(today),
                contentService.bestTime(dayCode),
                contentService.energyOfDay(dayCode),
                contentService.whatToDo(dayCode),
                contentService.whatToAvoid(dayCode),
                contentService.monthlyAstroEvent(today.getMonthValue()),
                affirmation,
                personalYearNum,
                personalMonthNum
        );

        String payload = serialize(response);

        NumerologyDayReading reading = NumerologyDayReading.builder()
                .userId(userId)
                .date(today)
                .dayCode(dayCode)
                .personalYearNumber(personalYearNum)
                .personalMonthNumber(personalMonthNum)
                .affirmation(affirmation)
                .payload(payload)
                .build();

        repository.save(reading);

        diaryService.save(userId, DiaryFeatureType.NUMEROLOGY_DAY, reading.getId(), response);

        return toResponse(reading);
    }

    private NumerologyDayResponse toResponse(NumerologyDayReading reading) {
        try {
            NumerologyDayResponse stored = objectMapper.readValue(reading.getPayload(), NumerologyDayResponse.class);
            // Подставляем актуальный id из БД
            return new NumerologyDayResponse(
                    reading.getId(),
                    stored.date(),
                    stored.dayCode(),
                    stored.dayCodeTitle(),
                    stored.lifePathNumber(),
                    stored.lifePathTitle(),
                    stored.moonPhase(),
                    stored.zodiacSign(),
                    stored.bestTime(),
                    stored.energyOfDay(),
                    stored.whatToDo(),
                    stored.whatToAvoid(),
                    stored.astroEvent(),
                    stored.affirmation(),
                    stored.personalYearNumber(),
                    stored.personalMonthNumber()
            );
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize NumerologyDayReading payload id={}", reading.getId(), e);
            throw new IllegalStateException("Ошибка чтения нумерологических данных", e);
        }
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ошибка сериализации нумерологических данных", e);
        }
    }
}
