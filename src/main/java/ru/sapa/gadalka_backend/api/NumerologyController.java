package ru.sapa.gadalka_backend.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.sapa.gadalka_backend.api.dto.numerology.NumerologyDayResponse;
import ru.sapa.gadalka_backend.api.dto.numerology.NumerologyWeekResponse;
import ru.sapa.gadalka_backend.service.NumerologyDayService;
import ru.sapa.gadalka_backend.service.NumerologyWeekService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/numerology")
@Tag(name = "Нумерология", description = "Личная нумерология пользователя")
public class NumerologyController extends BaseController {

    private final NumerologyDayService numerologyDayService;
    private final NumerologyWeekService numerologyWeekService;

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
                    """)
    public NumerologyWeekResponse getWeek(HttpServletRequest request) {
        return numerologyWeekService.getWeek(resolveUser(request).getId());
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
}
