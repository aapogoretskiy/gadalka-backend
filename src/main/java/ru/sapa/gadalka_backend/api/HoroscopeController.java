package ru.sapa.gadalka_backend.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sapa.gadalka_backend.api.dto.horoscope.DailyHoroscopeResponse;
import ru.sapa.gadalka_backend.service.HoroscopeService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/horoscope")
@Tag(name = "Гороскоп", description = "Гороскоп на день по знаку зодиака")
public class HoroscopeController extends BaseController {

    private final HoroscopeService horoscopeService;

    @GetMapping("/daily")
    @Operation(
            summary = "Гороскоп на день",
            description = """
                    Бесплатная фича. Знак зодиака определяется по дате рождения из профиля
                    (422, если дата рождения не указана). Текст гороскопа общий для всех
                    пользователей с одним знаком и обновляется один раз в сутки по московскому времени.
                    """)
    public DailyHoroscopeResponse getDaily(HttpServletRequest request) {
        return horoscopeService.getDailyHoroscope(resolveUser(request).getId());
    }
}
