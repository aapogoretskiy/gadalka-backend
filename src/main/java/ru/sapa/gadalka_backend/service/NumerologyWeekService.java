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
import ru.sapa.gadalka_backend.api.dto.numerology.NumerologyWeekPeakDayDto;
import ru.sapa.gadalka_backend.api.dto.numerology.NumerologyWeekResponse;
import ru.sapa.gadalka_backend.domain.NumerologyWeekReading;
import ru.sapa.gadalka_backend.domain.UserProfile;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;
import ru.sapa.gadalka_backend.repository.NumerologyWeekReadingRepository;
import ru.sapa.gadalka_backend.repository.NumerologyYearReadingRepository;
import ru.sapa.gadalka_backend.repository.UserProfileRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
    private final NumerologyYearReadingRepository yearReadingRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;
    private final DiaryService diaryService;
    private final FortuneCreditService fortuneCreditService;
    private final FeatureCostService featureCostService;
    private final ObjectMapper objectMapper;

    @Transactional
    public NumerologyWeekResponse getWeek(Long userId) {
        LocalDate today = LocalDate.now();

        return currentWeekReading(userId, today)
                .map(this::toResponse)
                .orElseGet(() -> createAndSave(userId, today, today.plusDays(6), !ownsYearCovering(userId, today)));
    }

    /**
     * Тихая проверка наличия уже оплаченного расклада на текущую неделю — НЕ списывает знаки.
     * Используется фронтом при открытии экрана, чтобы понять, показывать ли сразу готовый
     * результат или пейволл.
     * <p>
     * Если расклада ещё нет, но пользователь владеет годовым разбором, покрывающим сегодняшний
     * день, — молча создаём стандартную 7-дневную неделю БЕСПЛАТНО вместо 404/пейволла: год уже
     * оплачен, неделя входит в его стоимость. Если позже пользователь откроет через экран года
     * ещё и сам месяц — его календарные недельные блоки будут сосуществовать с этой; какая из
     * них "актуальна на сегодня" решает {@link #currentWeekReading} (берёт последнюю по дате
     * начала), это уже штатный сценарий пересечения диапазонов.
     */
    @Transactional
    public Optional<NumerologyWeekResponse> peekWeek(Long userId) {
        LocalDate today = LocalDate.now();

        Optional<NumerologyWeekResponse> existing = currentWeekReading(userId, today).map(this::toResponse);
        if (existing.isPresent()) {
            return existing;
        }
        if (ownsYearCovering(userId, today)) {
            return Optional.of(createAndSave(userId, today, today.plusDays(6), false));
        }
        return Optional.empty();
    }

    /** Владеет ли пользователь годовым разбором, покрывающим переданную дату. */
    private boolean ownsYearCovering(Long userId, LocalDate date) {
        LocalDate yearStart = LocalDate.of(date.getYear(), 1, 1);
        return yearReadingRepository.findByUserIdAndYearStartDate(userId, yearStart).isPresent();
    }

    /**
     * Находит расклад, покрывающий сегодняшний день. Если у пользователя есть отдельно купленная
     * неделя (плавающее окно от даты покупки) и недели, включённые в купленный месяц (фиксированные
     * календарные блоки), их диапазоны могут пересекаться — в этом случае берём запись с самой
     * поздней датой начала как самую «актуальную» на сегодня.
     */
    private Optional<NumerologyWeekReading> currentWeekReading(Long userId, LocalDate today) {
        List<NumerologyWeekReading> matches = repository
                .findByUserIdAndWeekStartDateLessThanEqualAndWeekEndDateGreaterThanEqualOrderByWeekStartDateDesc(userId, today, today);
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
    }

    /**
     * Тихая проверка расклада на КОНКРЕТНУЮ дату начала недели — используется, когда пользователь
     * открывает одну из 4 недель внутри уже купленного месячного разбора (см. NumerologyMonthService).
     * НЕ создаёт расклад и НЕ списывает знаки — просто отдаёт уже существующий (созданный бесплатно
     * при покупке месяца через {@link #createIncludedWeek}) или пусто, если такого ещё нет.
     */
    @Transactional
    public Optional<NumerologyWeekResponse> peekByDate(Long userId, LocalDate weekStart) {
        return repository.findByUserIdAndWeekStartDate(userId, weekStart)
                .map(this::toResponse);
    }

    /**
     * Создаёт (если ещё не существует) расклад на неделю с произвольной датой начала БЕЗ списания
     * знаков — используется при покупке месячного разбора: 4 недели месяца входят в его стоимость
     * и открываются пользователю бесплатно и сразу (см. NumerologyMonthService.createAndSave).
     * Стандартная неделя — ровно 7 дней (weekStart + 6).
     */
    @Transactional
    public NumerologyWeekResponse createIncludedWeek(Long userId, LocalDate weekStart) {
        return createIncludedWeek(userId, weekStart, weekStart.plusDays(6));
    }

    /**
     * То же самое, но с явной датой окончания — нужно для «хвостового» 5-го блока месяца
     * (29-е число и позже), который короче обычной недели: 1-3 дня в зависимости от длины
     * календарного месяца (см. NumerologyMonthService.createAndSave).
     */
    @Transactional
    public NumerologyWeekResponse createIncludedWeek(Long userId, LocalDate weekStart, LocalDate weekEnd) {
        return repository.findByUserIdAndWeekStartDate(userId, weekStart)
                .map(this::toResponse)
                .orElseGet(() -> createAndSave(userId, weekStart, weekEnd, false));
    }

    private NumerologyWeekResponse createAndSave(Long userId, LocalDate weekStart, LocalDate weekEnd, boolean chargeCredits) {
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

        // Три пиковых дня недели — топ-3 по резонансу, с коротким советом по коду конкретного дня
        List<NumerologyWeekPeakDayDto> peakDays = days.stream()
                .sorted(Comparator.comparingInt(NumerologyWeekDayDto::resonanceScore).reversed())
                .limit(3)
                .map(d -> new NumerologyWeekPeakDayDto(
                        d.date(),
                        d.dayOfWeek(),
                        d.dayCodeTitle(),
                        contentService.peakAdvice(d.dayCode())))
                .toList();

        String mainTheme = contentService.weekMainTheme(weekNumber);
        String whatToStrengthen = contentService.weekWhatToStrengthen(weekNumber);
        String whatToAvoidWeek = contentService.weekWhatToAvoid(weekNumber);
        String relationships = contentService.weekRelationships(weekNumber);
        String finance = contentService.weekFinance(weekNumber);

        int weekCost = featureCostService.getNumerologyWeekCost();
        if (chargeCredits) {
            fortuneCreditService.spendCredits(userId, DiaryFeatureType.NUMEROLOGY_WEEK, weekCost);
        }

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
                weeklyAffirmation,
                mainTheme,
                peakDays,
                whatToStrengthen,
                whatToAvoidWeek,
                relationships,
                finance
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

        if (chargeCredits) {
            log.info("Недельный нумерологический расклад создан и оплачен: userId={}, weekStart={}, weekEnd={}, списано={} знаков",
                    userId, weekStart, weekEnd, weekCost);
        } else {
            log.info("Недельный нумерологический расклад создан бесплатно (включён в месячный разбор): userId={}, weekStart={}, weekEnd={}",
                    userId, weekStart, weekEnd);
        }

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
                    stored.weeklyAffirmation(),
                    stored.mainTheme(),
                    stored.peakDays(),
                    stored.whatToStrengthen(),
                    stored.whatToAvoid(),
                    stored.relationships(),
                    stored.finance()
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
