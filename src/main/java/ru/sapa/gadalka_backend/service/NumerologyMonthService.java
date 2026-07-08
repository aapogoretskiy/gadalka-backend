package ru.sapa.gadalka_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.sapa.gadalka_backend.api.dto.numerology.NumerologyMonthKeyDateDto;
import ru.sapa.gadalka_backend.api.dto.numerology.NumerologyMonthLifeAreasDto;
import ru.sapa.gadalka_backend.api.dto.numerology.NumerologyMonthResponse;
import ru.sapa.gadalka_backend.api.dto.numerology.NumerologyMonthWeekPreviewDto;
import ru.sapa.gadalka_backend.api.dto.numerology.NumerologyWeekResponse;
import ru.sapa.gadalka_backend.domain.NumerologyMonthReading;
import ru.sapa.gadalka_backend.domain.UserProfile;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;
import ru.sapa.gadalka_backend.repository.NumerologyMonthReadingRepository;
import ru.sapa.gadalka_backend.repository.UserProfileRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Платный месячный нумерологический разбор — «матрёшка» поверх недельного: месяц разбит на 4
 * недельных блока по 7 дней от 1-го числа (1–7, 8–14, 15–21, 22–28), и каждый из них —
 * это самый обычный {@link NumerologyWeekService} расклад, только созданный БЕСПЛАТНО
 * (входит в стоимость месяца, см. {@link NumerologyWeekService#createIncludedWeek}). Если в
 * месяце есть дни 29-31, к этим 4 добавляется короткий 5-й «хвостовой» блок (1-3 дня, до конца
 * месяца) — чтобы вообще все дни месяца попадали в какой-нибудь недельный блок, а не только
 * первые 28.
 * <p>
 * Окно расклада — календарный месяц. Пока today внутри [monthStart; monthEnd], повторные открытия
 * экрана отдают тот же сохранённый разбор бесплатно — повторное списание не происходит.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NumerologyMonthService {

    private final NumerologyService numerologyService;
    private final NumerologyContentService contentService;
    private final NumerologyMonthReadingRepository repository;
    private final NumerologyWeekService numerologyWeekService;
    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;
    private final DiaryService diaryService;
    private final FortuneCreditService fortuneCreditService;
    private final FeatureCostService featureCostService;
    private final ObjectMapper objectMapper;

    @Transactional
    public NumerologyMonthResponse getMonth(Long userId) {
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);

        return repository.findByUserIdAndMonthStartDate(userId, monthStart)
                .map(this::toResponse)
                .orElseGet(() -> createAndSave(userId, monthStart, true));
    }

    /**
     * Тихая проверка наличия уже оплаченного разбора на текущий месяц — НЕ создаёт новый
     * разбор и НЕ списывает знаки. Используется фронтом при открытии экрана.
     */
    @Transactional
    public Optional<NumerologyMonthResponse> peekMonth(Long userId) {
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);

        return repository.findByUserIdAndMonthStartDate(userId, monthStart)
                .map(this::toResponse);
    }

    /**
     * Тихая проверка расклада на КОНКРЕТНЫЙ месяц (произвольный, не обязательно текущий) —
     * используется, когда пользователь открывает один из 12 месяцев внутри уже купленного
     * годового разбора (см. NumerologyYearService). НЕ создаёт разбор и НЕ списывает знаки —
     * просто отдаёт уже существующий (созданный бесплатно при клике через
     * {@link #createIncludedMonth}) или пусто, если такого ещё нет.
     */
    @Transactional
    public Optional<NumerologyMonthResponse> peekByDate(Long userId, LocalDate monthStart) {
        return repository.findByUserIdAndMonthStartDate(userId, monthStart)
                .map(this::toResponse);
    }

    /**
     * Создаёт (если ещё не существует) разбор на произвольный календарный месяц БЕЗ списания
     * знаков — используется, когда пользователь кликает на один из 12 месяцев годового разбора:
     * полный разбор месяца входит в стоимость года и открывается бесплатно, лениво, по клику
     * (см. NumerologyYearService). Как и обычный месяц, попутно бесплатно создаёт свои 4-5
     * недельных блоков через {@link NumerologyWeekService#createIncludedWeek}.
     */
    @Transactional
    public NumerologyMonthResponse createIncludedMonth(Long userId, LocalDate monthStart) {
        return repository.findByUserIdAndMonthStartDate(userId, monthStart)
                .map(this::toResponse)
                .orElseGet(() -> createAndSave(userId, monthStart, false));
    }

    private NumerologyMonthResponse createAndSave(Long userId, LocalDate monthStart, boolean chargeCredits) {
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
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
        int calendarMonth = monthStart.getMonthValue();
        int lifePathNumber = numerologyService.lifePathNumber(birthDate);
        int monthNumber = numerologyService.personalMonthNumber(birthDate, monthStart.getYear(), calendarMonth);

        String monthNumberTitle = contentService.lifePathTitle(monthNumber);
        String mainTheme = contentService.monthMainTheme(monthNumber, calendarMonth);
        NumerologyMonthLifeAreasDto lifeAreas = contentService.monthLifeAreas(monthNumber, calendarMonth);
        String whatToAvoid = contentService.monthWhatToAvoid(monthNumber, calendarMonth);
        String advice = contentService.monthAdvice(monthNumber, calendarMonth);

        // Резонанс каждого дня месяца (та же формула, что и для дня/недели) — источник для ключевых дат
        List<DayResonance> allDays = monthStart.datesUntil(monthEnd.plusDays(1))
                .map(date -> {
                    int dayCode = numerologyService.personalDayCode(birthDate, date);
                    int score = numerologyService.numberAffinity(lifePathNumber, dayCode);
                    return new DayResonance(date, dayCode, score);
                })
                .toList();

        // 4 недельных блока по 7 дней от 1-го числа месяца: 1-7, 8-14, 15-21, 22-28.
        // Плюс, если в месяце есть дни 29-31, добавляется короткий 5-й «хвостовой» блок
        // (1-3 дня, до конца месяца) — иначе эти дни не попадали бы ни в одну неделю
        // месяца, хотя формально участвуют в расчёте резонанса наравне со всеми
        // остальными днями (см. allDays выше, там уже весь месяц целиком).
        List<WeekBlock> blocks = new ArrayList<>();
        blocks.add(new WeekBlock(monthStart, monthStart.plusDays(6)));
        blocks.add(new WeekBlock(monthStart.plusDays(7), monthStart.plusDays(13)));
        blocks.add(new WeekBlock(monthStart.plusDays(14), monthStart.plusDays(20)));
        blocks.add(new WeekBlock(monthStart.plusDays(21), monthStart.plusDays(27)));
        LocalDate tailStart = monthStart.plusDays(28); // 29-е число месяца
        if (!tailStart.isAfter(monthEnd)) {
            blocks.add(new WeekBlock(tailStart, monthEnd));
        }

        List<NumerologyMonthKeyDateDto> keyDates = buildKeyDates(allDays, blocks);

        int monthCost = featureCostService.getNumerologyMonthCost();
        if (chargeCredits) {
            fortuneCreditService.spendCredits(userId, DiaryFeatureType.NUMEROLOGY_MONTH, monthCost);
        }

        // Недели внутри месяца включены в его стоимость — создаём (или переиспользуем уже
        // существующие) 4-5 недельных раскладов БЕСПЛАТНО, знаки за них не списываются.
        List<NumerologyMonthWeekPreviewDto> weekPreviews = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            WeekBlock block = blocks.get(i);
            NumerologyWeekResponse week = numerologyWeekService.createIncludedWeek(userId, block.start(), block.end());
            int weekResonance = numerologyService.numberAffinity(lifePathNumber, week.weekNumber());
            weekPreviews.add(new NumerologyMonthWeekPreviewDto(
                    i + 1,
                    week.weekStart(),
                    week.weekEnd(),
                    week.weekNumber(),
                    week.weekNumberTitle(),
                    resonanceLabel(weekResonance)
            ));
        }

        NumerologyMonthResponse response = new NumerologyMonthResponse(
                null,
                monthStart,
                monthEnd,
                monthNumber,
                monthNumberTitle,
                mainTheme,
                lifeAreas,
                keyDates,
                whatToAvoid,
                advice,
                weekPreviews
        );

        String payload = serialize(response);

        NumerologyMonthReading reading = NumerologyMonthReading.builder()
                .userId(userId)
                .monthStartDate(monthStart)
                .monthEndDate(monthEnd)
                .monthNumber(monthNumber)
                .payload(payload)
                .build();

        repository.save(reading);
        userRepository.incrementActionsCount(userId);

        diaryService.save(userId, DiaryFeatureType.NUMEROLOGY_MONTH, reading.getId(), response);

        if (chargeCredits) {
            log.info("Месячный нумерологический разбор создан и оплачен: userId={}, monthStart={}, monthEnd={}, списано={} знаков",
                    userId, monthStart, monthEnd, monthCost);
        } else {
            log.info("Месячный нумерологический разбор создан бесплатно (включён в годовой разбор): userId={}, monthStart={}, monthEnd={}",
                    userId, monthStart, monthEnd);
        }

        return toResponse(reading);
    }

    /**
     * Ключевые даты месяца. «Пик» и «Осторожно» — это ГЛОБАЛЬНЫЙ лучший и худший день
     * резонанса за весь месяц (та же формула personalDayCode + numberAffinity, что и на
     * экране недели) — иначе «Осторожно» может ссылаться на день, который сам по себе
     * благоприятный, просто слабее других кандидатов, и это будет противоречить тому,
     * что покажет открытая неделя для той же даты.
     * <p>
     * «Решения» и «Встреча» — нейтральные по смыслу бейджи (не про хорошо/плохо), их
     * распределяем по недельным блокам (включая короткий «хвостовой» 5-й блок, если он
     * есть), не занятым пиком/осторожно, чтобы даты были равномерно раскиданы по месяцу,
     * а не скучены рядом. Благодаря 5-му блоку дни 29-31 тоже участвуют в этом отборе.
     */
    private List<NumerologyMonthKeyDateDto> buildKeyDates(List<DayResonance> allDays, List<WeekBlock> blocks) {
        DayResonance peak = allDays.stream()
                .max(Comparator.comparingInt(DayResonance::score))
                .orElseThrow();
        DayResonance caution = allDays.stream()
                .min(Comparator.comparingInt(DayResonance::score))
                .orElseThrow();

        List<DayResonance> blockChampions = new ArrayList<>();
        for (WeekBlock block : blocks) {
            allDays.stream()
                    .filter(d -> !d.date().isBefore(block.start()) && !d.date().isAfter(block.end()))
                    .filter(d -> !d.date().equals(peak.date()) && !d.date().equals(caution.date()))
                    .max(Comparator.comparingInt(DayResonance::score))
                    .ifPresent(blockChampions::add);
        }

        List<DayResonance> remaining = blockChampions.stream()
                .sorted(Comparator.comparing(DayResonance::date))
                .toList();

        List<NumerologyMonthKeyDateDto> result = new ArrayList<>();
        result.add(new NumerologyMonthKeyDateDto(peak.date(), "Пик", contentService.peakAdvice(peak.dayCode())));
        result.add(new NumerologyMonthKeyDateDto(caution.date(), "Осторожно", contentService.whatToAvoid(caution.dayCode())));
        if (!remaining.isEmpty()) {
            result.add(new NumerologyMonthKeyDateDto(remaining.get(0).date(), "Решения",
                    "День решений. То, что вы выберете сегодня — согласиться или отказаться, ждать или "
                    + "действовать — задаст тон на ближайшие недели. Не откладывайте важный разговор "
                    + "или подпись под документом на потом: сейчас у вас есть ясность, которой не будет позже."));
        }
        if (remaining.size() > 1) {
            result.add(new NumerologyMonthKeyDateDto(remaining.get(remaining.size() - 1).date(), "Встреча",
                    "Возможна неожиданная встреча, звонок или предложение, которое на первый взгляд "
                    + "покажется случайным. Присмотритесь к новым именам и знакомствам этого дня — "
                    + "именно из таких случайностей часто вырастает то, что меняет ход месяца."));
        }

        return result.stream().sorted(Comparator.comparing(NumerologyMonthKeyDateDto::date)).toList();
    }

    private String resonanceLabel(int score) {
        if (score >= 75) return "Благоприятный";
        if (score >= 55) return "Нейтральный";
        return "Будьте внимательнее";
    }

    private NumerologyMonthResponse toResponse(NumerologyMonthReading reading) {
        try {
            NumerologyMonthResponse stored = objectMapper.readValue(reading.getPayload(), NumerologyMonthResponse.class);
            return new NumerologyMonthResponse(
                    reading.getId(),
                    stored.monthStart(),
                    stored.monthEnd(),
                    stored.monthNumber(),
                    stored.monthNumberTitle(),
                    stored.mainTheme(),
                    stored.lifeAreas(),
                    stored.keyDates(),
                    stored.whatToAvoid(),
                    stored.advice(),
                    stored.weekPreviews()
            );
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize NumerologyMonthReading payload id={}", reading.getId(), e);
            throw new IllegalStateException("Ошибка чтения нумерологических данных месяца", e);
        }
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ошибка сериализации нумерологических данных месяца", e);
        }
    }

    private record DayResonance(LocalDate date, int dayCode, int score) {
    }

    /** Границы одного недельного блока внутри месяца (включительно). */
    private record WeekBlock(LocalDate start, LocalDate end) {
    }
}
