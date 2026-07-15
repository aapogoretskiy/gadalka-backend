package ru.sapa.gadalka_backend.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.sapa.gadalka_backend.api.dto.profile.UpdateProfileRequest;
import ru.sapa.gadalka_backend.service.UserProfileService;
import ru.sapa.gadalka_backend.api.dto.numerology.NumerologyDayResponse;
import ru.sapa.gadalka_backend.api.dto.numerology.NumerologyMonthResponse;
import ru.sapa.gadalka_backend.api.dto.numerology.NumerologyPortraitResponse;
import ru.sapa.gadalka_backend.api.dto.numerology.NumerologyWeekResponse;
import ru.sapa.gadalka_backend.api.dto.numerology.NumerologyYearResponse;
import ru.sapa.gadalka_backend.domain.type.SpendMode;
import ru.sapa.gadalka_backend.service.NumerologyDayService;
import ru.sapa.gadalka_backend.service.NumerologyMonthService;
import ru.sapa.gadalka_backend.service.NumerologyPortraitService;
import ru.sapa.gadalka_backend.service.NumerologyWeekService;
import ru.sapa.gadalka_backend.service.NumerologyYearService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/numerology")
@Tag(name = "Нумерология", description = "Личная нумерология пользователя")
public class NumerologyController extends BaseController {

    private final NumerologyDayService numerologyDayService;
    private final NumerologyWeekService numerologyWeekService;
    private final NumerologyMonthService numerologyMonthService;
    private final NumerologyYearService numerologyYearService;
    private final NumerologyPortraitService numerologyPortraitService;
    private final UserProfileService userProfileService;

    @GetMapping("/today")
    public NumerologyDayResponse getToday(HttpServletRequest request) {
        return numerologyDayService.getToday(resolveUser(request).getId());
    }

    @GetMapping("/week")
    @Operation(
            summary = "Недельный нумерологический расклад",
            description = """
                    Платный расклад на 7 дней начиная с сегодняшней даты.
                    Если у пользователя уже есть оплаченный и ещё действующий расклад (today входит в его 7-дневное окно),
                    повторное списание не происходит — отдаётся тот же расклад.
                    Требует указанной даты рождения в профиле (422, если её нет) и достаточного баланса знаков (402, если не хватает).
                    Параметр spendMode: CREDITS (знаки, по умолчанию) или QUOTA (квота подписки).
                    """)
    public NumerologyWeekResponse getWeek(HttpServletRequest request,
                                          @RequestParam(defaultValue = "CREDITS") SpendMode spendMode) {
        return numerologyWeekService.getWeek(resolveUser(request).getId(), spendMode);
    }

    @GetMapping("/portrait")
    @Operation(
            summary = "Нумерологический портрет личности",
            description = """
                    Возвращает все постоянные числа пользователя: число жизни, число дня рождения,
                    число души и число имени. Расчёт числа души и числа имени выполняется по имени
                    из профиля (numerologyName), если оно задано, иначе — по имени из Telegram.
                    Поле nameSource: "custom" — использовано пользовательское имя, "telegram" — TG-имя.
                    Требует наличия даты рождения (422, если отсутствует).
                    """)
    public NumerologyPortraitResponse getPortrait(HttpServletRequest request) {
        return numerologyPortraitService.getPortrait(resolveUser(request).getId());
    }

    @PatchMapping("/portrait/name")
    @Operation(
            summary = "Сохранить имя для нумерологического портрета",
            description = """
                    Сохраняет пользовательское имя для расчёта числа души и числа имени.
                    Передайте пустую строку, чтобы сбросить имя (вернётся к TG-имени).
                    """)
    public void savePortraitName(HttpServletRequest request, @RequestParam String name) {
        userProfileService.updateProfile(
                resolveUser(request).getId(),
                new UpdateProfileRequest(null, null, null, null, null, name)
        );
    }

    @GetMapping("/week/current")
    @Operation(
            summary = "Тихая проверка действующего расклада на неделю",
            description = """
                    В отличие от /week — НЕ создаёт новый расклад и НЕ списывает знаки.
                    Возвращает уже оплаченный и действующий расклад, если он есть, иначе 404.
                    Используется фронтом при открытии экрана, чтобы решить — показать готовый
                    результат или пейволл, без риска случайно инициировать платную покупку.
                    """)
    public NumerologyWeekResponse getCurrentWeek(HttpServletRequest request) {
        return numerologyWeekService.peekWeek(resolveUser(request).getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Действующего расклада на неделю нет"));
    }

    @GetMapping("/week/by-date")
    @Operation(
            summary = "Расклад на неделю по конкретной дате начала",
            description = """
                    Тихая бесплатная отдача уже существующего расклада с точной датой начала недели
                    (формат YYYY-MM-DD) — НЕ создаёт новый расклад и НЕ списывает знаки.
                    Используется при переходе на одну из 4 недель внутри уже купленного месячного
                    разбора (они создаются бесплатно при покупке месяца), а также для повторного
                    открытия любой ранее полученной недели. 404, если расклада с такой датой нет.
                    """)
    public NumerologyWeekResponse getWeekByDate(HttpServletRequest request, @RequestParam String date) {
        LocalDate weekStart = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
        return numerologyWeekService.peekByDate(resolveUser(request).getId(), weekStart)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Расклада на эту неделю нет"));
    }

    @GetMapping("/month")
    @Operation(
            summary = "Месячный нумерологический разбор",
            description = """
                    Платный разбор на текущий календарный месяц. Если у пользователя уже есть оплаченный
                    разбор на этот месяц, повторное списание не происходит — отдаётся тот же разбор.
                    4 недели внутри месяца включены в стоимость и создаются бесплатно.
                    Требует указанной даты рождения в профиле (422) и достаточного баланса знаков (402).
                    """)
    public NumerologyMonthResponse getMonth(HttpServletRequest request,
                                            @RequestParam(defaultValue = "CREDITS") SpendMode spendMode) {
        return numerologyMonthService.getMonth(resolveUser(request).getId(), spendMode);
    }

    @GetMapping("/month/current")
    @Operation(
            summary = "Тихая проверка действующего разбора на месяц",
            description = """
                    В отличие от /month — НЕ создаёт новый разбор и НЕ списывает знаки.
                    Возвращает уже оплаченный разбор на текущий месяц, если он есть, иначе 404.
                    """)
    public NumerologyMonthResponse getCurrentMonth(HttpServletRequest request) {
        return numerologyMonthService.peekMonth(resolveUser(request).getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Действующего разбора на месяц нет"));
    }

    @GetMapping("/month/by-date")
    @Operation(
            summary = "Открыть месяц внутри уже купленного годового разбора",
            description = """
                    Возвращает полный разбор конкретного календарного месяца (параметр date — любое
                    число этого месяца, формат YYYY-MM-DD). Если разбор ещё не создан — создаёт его
                    бесплатно (включён в стоимость года) и заодно бесплатно создаёт его 4-5 недельных
                    блоков. Требует, чтобы у пользователя был куплен годовой разбор на год, к которому
                    относится месяц — иначе 402. Используется при переходе на месяц с экрана года.
                    """)
    public NumerologyMonthResponse getMonthByDate(HttpServletRequest request, @RequestParam String date) {
        LocalDate monthStart = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE).withDayOfMonth(1);
        return numerologyYearService.openIncludedMonth(resolveUser(request).getId(), monthStart);
    }

    @GetMapping("/year")
    @Operation(
            summary = "Годовой нумерологический разбор",
            description = """
                    Платный разбор на текущий календарный год. Если у пользователя уже есть оплаченный
                    разбор на этот год, повторное списание не происходит — отдаётся тот же разбор.
                    Экран года показывает 12 лёгких превью месяцев (считаются на лету, без создания
                    записей) и 4 ключевых периода (Старт/Пауза/Пик/Итоги) — полный разбор конкретного
                    месяца создаётся отдельно и бесплатно через /month/by-date по клику.
                    Требует указанной даты рождения в профиле (422) и достаточного баланса знаков (402).
                    """)
    public NumerologyYearResponse getYear(HttpServletRequest request,
                                          @RequestParam(defaultValue = "CREDITS") SpendMode spendMode) {
        return numerologyYearService.getYear(resolveUser(request).getId(), spendMode);
    }

    @GetMapping("/year/current")
    @Operation(
            summary = "Тихая проверка действующего разбора на год",
            description = """
                    В отличие от /year — НЕ создаёт новый разбор и НЕ списывает знаки.
                    Возвращает уже оплаченный разбор на текущий год, если он есть, иначе 404.
                    """)
    public NumerologyYearResponse getCurrentYear(HttpServletRequest request) {
        return numerologyYearService.peekYear(resolveUser(request).getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Действующего разбора на год нет"));
    }
}
