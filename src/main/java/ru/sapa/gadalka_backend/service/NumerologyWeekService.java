package ru.sapa.gadalka_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.sapa.gadalka_backend.api.dto.numerology.NumerologyWeekDayDto;
import ru.sapa.gadalka_backend.api.dto.numerology.NumerologyWeekResponse;
import ru.sapa.gadalka_backend.domain.NumerologyWeekReading;
import ru.sapa.gadalka_backend.domain.UserProfile;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;
import ru.sapa.gadalka_backend.repository.NumerologyWeekReadingRepository;
import ru.sapa.gadalka_backend.repository.UserProfileRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Платный недельный нумерологический расклад.
 * <p>
 * Стоимость берётся из {@link FeatureCostService#getNumerologyWeekCost()} (system_config,
 * редактируется в админ-панели). Изначально была синхронизирована со стоимостью расклада
 * «3 карты», но теперь это независимая настройка.
 * <p>
 * Окно расклада — 7 дней начиная с даты первой оплаты. Пока это окно не истекло (today лежит в [weekStart; weekEnd]),
 * повторные открытия экрана отдают тот же сохранённый расклад бесплатно — повторное списание не происходит.
 * Как только окно истекает, следующий запрос создаёт новый расклад с новым списанием знаков.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NumerologyWeekService {

    private final NumerologyService numerologyService;
    private final NumerologyContentService contentService;
    private final NumerologyWeekReadingRepository repository;
    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;
    private final DiaryService diaryService;
    private final FortuneCreditService fortuneCreditService;
    private final FeatureCostService featureCostService;
    private final ObjectMapper objectMapper;

    @Transactional
    public NumerologyWeekResponse getWeek(Long userId) {
        LocalDate today = LocalDate.now();

        return repository.findByUserIdAndWeekStartDateLessThanEqualAndWeekEndDateGreaterThanEqual(userId, today, today)
                .map(this::toResponse)
                .orElseGet(() -> createAndSave(userId, today));
    }

    private NumerologyWeekResponse createAndSave(Long userId, LocalDate weekStart) {
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
        LocalDate weekEnd = weekStart.plusDays(6);
        int lifePathNumber = numerologyService.lifePathNumber(birthDate);

        List<NumerologyWeekDayDto> days = weekStart.datesUntil(weekEnd.plusDays(1))
                .map(date -> buildDay(birthDate, date, lifePathNumber))
                .toList();

        NumerologyWeekDayDto bestDay = days.stream()
                .max(Comparator.comparingInt(NumerologyWeekDayDto::resonanceScore))
                .orElseThrow();
        NumerologyWeekDayDto challengingDay = days.stream()
                .min(Comparator.comparingInt(NumerologyWeekDayDto::resonanceScore))
                .orElseThrow();

        int weekNumber = numerologyService.reduceToNumerologyNumber(days.stream().mapToInt(NumerologyWeekDayDto::dayCode).sum());
        String weekNumberTitle = contentService.lifePathTitle(weekNumber);
        String weekDescription = contentService.energyOfDay(weekNumber);
        String weeklyAffirmation = contentService.randomAffirmation(weekNumber);

        int weekCost = featureCostService.getNumerologyWeekCost();
        fortuneCreditService.spendCredits(userId, DiaryFeatureType.NUMEROLOGY_WEEK, weekCost);

        NumerologyWeekResponse response = new NumerologyWeekResponse(
                null,
                weekStart,
                weekEnd,
                weekNumber,
                weekNumberTitle,
                weekDescription,
                days,
                bestDay,
                challengingDay,
                weeklyAffirmation
        );

        String payload = serialize(response);

        NumerologyWeekReading reading = NumerologyWeekReading.builder()
                .userId(userId)
                .weekStartDate(weekStart)
                .weekEndDate(weekEnd)
                .weekNumber(weekNumber)
                .payload(payload)
                .build();

        repository.save(reading);
        userRepository.incrementActionsCount(userId);

        diaryService.save(userId, DiaryFeatureType.NUMEROLOGY_WEEK, reading.getId(), response);

        log.info("Недельный нумерологический расклад создан и оплачен: userId={}, weekStart={}, weekEnd={}, списано={} знаков",
                userId, weekStart, weekEnd, weekCost);

        return toResponse(reading);
    }

    private NumerologyWeekDayDto buildDay(LocalDate birthDate, LocalDate date, int lifePathNumber) {
        int dayCode = numerologyService.personalDayCode(birthDate, date);
        int resonanceScore = numerologyService.numberAffinity(lifePathNumber, dayCode);

        return new NumerologyWeekDayDto(
                date,
                russianDayOfWeek(date.getDayOfWeek()),
                dayCode,
                contentService.title(dayCode),
                resonanceScore,
                resonanceLabel(resonanceScore)
        );
    }

    private String resonanceLabel(int score) {
        if (score >= 75) return "Благоприятный";
        if (score >= 55) return "Нейтральный";
        return "Будьте внимательнее";
    }

    private String russianDayOfWeek(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY    -> "Пн";
            case TUESDAY   -> "Вт";
            case WEDNESDAY -> "Ср";
            case THURSDAY  -> "Чт";
            case FRIDAY    -> "Пт";
            case SATURDAY  -> "Сб";
            case SUNDAY    -> "Вс";
        };
    }

    private NumerologyWeekResponse toResponse(NumerologyWeekReading reading) {
        try {
            NumerologyWeekResponse stored = objectMapper.readValue(reading.getPayload(), NumerologyWeekResponse.class);
            return new NumerologyWeekResponse(
                    reading.getId(),
                    stored.weekStart(),
                    stored.weekEnd(),
                    stored.weekNumber(),
                    stored.weekNumberTitle(),
                    stored.weekDescription(),
                    stored.days(),
                    stored.bestDay(),
                    stored.challengingDay(),
                    stored.weeklyAffirmation()
            );
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize NumerologyWeekReading payload id={}", reading.getId(), e);
            throw new IllegalStateException("Ошибка чтения нумерологических данных недели", e);
        }
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ошибка сериализации нумерологических данных недели", e);
        }
    }
}
