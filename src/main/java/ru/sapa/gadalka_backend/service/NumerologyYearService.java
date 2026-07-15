package ru.sapa.gadalka_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.sapa.gadalka_backend.api.dto.numerology.NumerologyMonthLifeAreasDto;
import ru.sapa.gadalka_backend.api.dto.numerology.NumerologyMonthResponse;
import ru.sapa.gadalka_backend.api.dto.numerology.NumerologyYearKeyPeriodDto;
import ru.sapa.gadalka_backend.api.dto.numerology.NumerologyYearMonthPreviewDto;
import ru.sapa.gadalka_backend.api.dto.numerology.NumerologyYearResponse;
import ru.sapa.gadalka_backend.domain.NumerologyYearReading;
import ru.sapa.gadalka_backend.domain.UserProfile;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;
import ru.sapa.gadalka_backend.domain.type.SpendMode;
import ru.sapa.gadalka_backend.repository.NumerologyYearReadingRepository;
import ru.sapa.gadalka_backend.repository.UserProfileRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Платный годовой нумерологический разбор — верхний уровень «матрёшки» (Год ⊃ Месяц ⊃ Неделя ⊃ День).
 * <p>
 * В отличие от месяца (который при покупке сразу же бесплатно создаёт все свои недели), год
 * НЕ создаёт 12 месяцев сразу — это было бы слишком тяжёлой операцией (до 12 месяцев × до 5 недель
 * каждый за одну покупку). Вместо этого на экране года показываются 12 лёгких превью месяцев,
 * посчитанных на лету по формуле личного месяца, БЕЗ сохранения в БД. Полный разбор конкретного
 * месяца создаётся лениво и бесплатно только по клику — см. {@link NumerologyMonthService#createIncludedMonth}.
 * <p>
 * Окно расклада — календарный год. Пока today внутри [yearStart; yearEnd], повторные открытия
 * экрана отдают тот же сохранённый разбор бесплатно — повторное списание не происходит.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NumerologyYearService {

    private final NumerologyService numerologyService;
    private final NumerologyContentService contentService;
    private final NumerologyYearReadingRepository repository;
    private final NumerologyMonthService numerologyMonthService;
    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;
    private final DiaryService diaryService;
    private final FeatureSpendService featureSpendService;
    private final FeatureCostService featureCostService;
    private final ObjectMapper objectMapper;

    /** 4 квартала года и бейдж, закреплённый за позицией квартала (не за резонансом). */
    private static final List<QuarterDef> QUARTERS = List.of(
            new QuarterDef("Старт", "start", List.of(1, 2, 3)),
            new QuarterDef("Пауза", "pause", List.of(4, 5, 6)),
            new QuarterDef("Пик", "peak", List.of(7, 8, 9)),
            new QuarterDef("Итоги", "finale", List.of(10, 11, 12))
    );

    @Transactional
    public NumerologyYearResponse getYear(Long userId, SpendMode spendMode) {
        LocalDate yearStart = LocalDate.now().withDayOfYear(1);

        return repository.findByUserIdAndYearStartDate(userId, yearStart)
                .map(this::toResponse)
                .orElseGet(() -> createAndSave(userId, yearStart, spendMode));
    }

    /**
     * Тихая проверка наличия уже оплаченного разбора на текущий год — НЕ создаёт новый
     * разбор и НЕ списывает знаки. Используется фронтом при открытии экрана.
     */
    @Transactional
    public Optional<NumerologyYearResponse> peekYear(Long userId) {
        LocalDate yearStart = LocalDate.now().withDayOfYear(1);

        return repository.findByUserIdAndYearStartDate(userId, yearStart)
                .map(this::toResponse);
    }

    private NumerologyYearResponse createAndSave(Long userId, LocalDate yearStart, SpendMode spendMode) {
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
        LocalDate yearEnd = yearStart.withDayOfYear(yearStart.lengthOfYear());
        int calendarYear = yearStart.getYear();
        int lifePathNumber = numerologyService.lifePathNumber(birthDate);
        int yearNumber = numerologyService.personalYearNumber(birthDate, calendarYear);

        String yearTitle = contentService.yearTitle(yearNumber);
        String mainTheme = contentService.yearMainTheme(yearNumber, calendarYear);
        NumerologyMonthLifeAreasDto lifeAreas = contentService.yearLifeAreas(yearNumber, calendarYear);
        String whatToAvoid = contentService.yearWhatToAvoid(yearNumber, calendarYear);
        String advice = contentService.yearAdvice(yearNumber, calendarYear);

        // Резонанс каждого из 12 месяцев (personalMonthNumber + numberAffinity с числом жизни) —
        // считается на лету, ничего не сохраняет и не создаёт недель/месяцев.
        List<MonthResonance> monthResonances = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            int monthNumber = numerologyService.personalMonthNumber(birthDate, calendarYear, month);
            int score = numerologyService.numberAffinity(lifePathNumber, monthNumber);
            monthResonances.add(new MonthResonance(
                    month,
                    contentService.monthNameNominative(month),
                    monthNumber,
                    contentService.lifePathTitle(monthNumber),
                    score
            ));
        }

        List<NumerologyYearMonthPreviewDto> monthPreviews = monthResonances.stream()
                .map(m -> new NumerologyYearMonthPreviewDto(
                        m.calendarMonth(), m.monthName(), m.monthNumber(), m.monthNumberTitle(), resonanceLabel(m.score())))
                .toList();

        List<NumerologyYearKeyPeriodDto> keyPeriods = buildKeyPeriods(monthResonances);

        int yearCost = featureCostService.getNumerologyYearCost();
        featureSpendService.spend(userId, DiaryFeatureType.NUMEROLOGY_YEAR, yearCost, spendMode);

        NumerologyYearResponse response = new NumerologyYearResponse(
                null,
                yearStart,
                yearEnd,
                yearNumber,
                yearTitle,
                mainTheme,
                lifeAreas,
                keyPeriods,
                whatToAvoid,
                advice,
                monthPreviews
        );

        String payload = serialize(response);

        NumerologyYearReading reading = NumerologyYearReading.builder()
                .userId(userId)
                .yearStartDate(yearStart)
                .yearEndDate(yearEnd)
                .yearNumber(yearNumber)
                .payload(payload)
                .build();

        repository.save(reading);
        userRepository.incrementActionsCount(userId);

        diaryService.save(userId, DiaryFeatureType.NUMEROLOGY_YEAR, reading.getId(), response);

        log.info("Годовой нумерологический разбор создан и оплачен: userId={}, yearStart={}, yearEnd={}, списано={} знаков",
                userId, yearStart, yearEnd, yearCost);

        return toResponse(reading);
    }

    /**
     * Открывает (создаёт, если ещё не создан, бесплатно) полный разбор ОДНОГО конкретного месяца
     * из уже купленного годового разбора — вызывается по клику на карточку месяца на экране года.
     * Перед созданием проверяет, что у пользователя действительно куплен год, к которому относится
     * этот месяц — без этой проверки endpoint превратился бы в способ получить любой произвольный
     * месяц (в т.ч. из будущих лет) бесплатно, минуя оплату и месяца, и года.
     */
    @Transactional
    public NumerologyMonthResponse openIncludedMonth(Long userId, LocalDate monthStart) {
        LocalDate yearStart = LocalDate.of(monthStart.getYear(), 1, 1);

        repository.findByUserIdAndYearStartDate(userId, yearStart)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.PAYMENT_REQUIRED,
                        "Для доступа к этому месяцу нужно сначала приобрести годовой разбор на " + monthStart.getYear() + " год"));

        return numerologyMonthService.createIncludedMonth(userId, monthStart);
    }

    /**
     * 4 ключевых периода года. Позиция бейджа за кварталом ЗАФИКСИРОВАНА (Q1 всегда «Старт»,
     * Q2 — «Пауза», Q3 — «Пик», Q4 — «Итоги») — это сезонный, не резонансный смысл. А вот КАКОЙ
     * месяц внутри квартала получит этот бейдж — определяется самым высоким резонансом месяца
     * с числом жизненного пути пользователя (та же формула, что и для дня/недели/месяца).
     */
    private List<NumerologyYearKeyPeriodDto> buildKeyPeriods(List<MonthResonance> monthResonances) {
        List<NumerologyYearKeyPeriodDto> result = new ArrayList<>();

        for (QuarterDef quarter : QUARTERS) {
            MonthResonance winner = monthResonances.stream()
                    .filter(m -> quarter.months().contains(m.calendarMonth()))
                    .max(Comparator.comparingInt(MonthResonance::score))
                    .orElseThrow();

            String description = contentService.yearPeriodAdvice(winner.monthNumber(), quarter.contentKey());
            result.add(new NumerologyYearKeyPeriodDto(
                    quarter.badge(),
                    winner.calendarMonth(),
                    winner.monthName(),
                    winner.monthNumber(),
                    winner.monthNumberTitle(),
                    description
            ));
        }

        return result;
    }

    /** Те же пороги, что и для дня/недели: >=75 благоприятный, >=55 нейтральный, иначе — «внимательнее». */
    private String resonanceLabel(int score) {
        if (score >= 75) return "Благоприятный";
        if (score >= 55) return "Нейтральный";
        return "Будьте внимательнее";
    }

    private NumerologyYearResponse toResponse(NumerologyYearReading reading) {
        try {
            NumerologyYearResponse stored = objectMapper.readValue(reading.getPayload(), NumerologyYearResponse.class);
            return new NumerologyYearResponse(
                    reading.getId(),
                    stored.yearStart(),
                    stored.yearEnd(),
                    stored.yearNumber(),
                    stored.yearTitle(),
                    stored.mainTheme(),
                    stored.lifeAreas(),
                    stored.keyPeriods(),
                    stored.whatToAvoid(),
                    stored.advice(),
                    stored.monthPreviews()
            );
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize NumerologyYearReading payload id={}", reading.getId(), e);
            throw new IllegalStateException("Ошибка чтения нумерологических данных года", e);
        }
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ошибка сериализации нумерологических данных года", e);
        }
    }

    private record MonthResonance(int calendarMonth, String monthName, int monthNumber, String monthNumberTitle, int score) {
    }

    private record QuarterDef(String badge, String contentKey, List<Integer> months) {
    }
}
