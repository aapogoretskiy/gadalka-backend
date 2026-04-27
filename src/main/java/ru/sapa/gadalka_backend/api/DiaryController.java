package ru.sapa.gadalka_backend.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.sapa.gadalka_backend.api.dto.diary.DiaryEntryDto;
import ru.sapa.gadalka_backend.api.dto.diary.DiaryHistoryResponse;
import ru.sapa.gadalka_backend.api.dto.diary.DiarySaveRequest;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;
import ru.sapa.gadalka_backend.service.DiaryService;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/diary")
@RequiredArgsConstructor
@Tag(name = "Дневник", description = "История результатов функционала пользователя")
public class DiaryController extends BaseController {

    private final DiaryService diaryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Сохранить запись в дневник",
            description = """
                    Вручную добавляет существующий результат в дневник пользователя.
                    Передайте тип функционала и ID соответствующей записи (Fortune, CompatibilityReading или DailyCard).
                    Запись должна принадлежать текущему пользователю, иначе вернётся 404.
                    """)
    public DiaryEntryDto saveEntry(@Valid @RequestBody DiarySaveRequest body,
                                   HttpServletRequest request) {
        return diaryService.saveByReference(resolveUser(request), body.getFeatureType(), body.getReferenceId());
    }

    @GetMapping
    @Operation(
            summary = "История по типу функционала",
            description = """
                    Возвращает историю записей текущего пользователя за указанный диапазон дат.

                    **featureType** — тип функционала:
                    - `THREE_CARD` — расклад «3 карты»
                    - `COMPATIBILITY` — совместимость
                    - `DAILY_CARD` — карта дня

                    Диапазон дат задаётся параметрами `from` и `to` в формате `yyyy-MM-dd` (включительно).
                    Записи возвращаются в порядке убывания даты создания.
                    """)
    public DiaryHistoryResponse getHistory(
            @Parameter(description = "Тип функционала", required = true)
            @RequestParam @NotNull DiaryFeatureType featureType,

            @Parameter(description = "Начало диапазона (yyyy-MM-dd)", required = true)
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,

            @Parameter(description = "Конец диапазона (yyyy-MM-dd)", required = true)
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,

            HttpServletRequest request
    ) {
        return diaryService.getHistory(resolveUser(request).getId(), featureType, from, to);
    }
}
