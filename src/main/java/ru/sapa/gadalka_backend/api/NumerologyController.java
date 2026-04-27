package ru.sapa.gadalka_backend.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sapa.gadalka_backend.api.dto.numerology.NumerologyDayResponse;
import ru.sapa.gadalka_backend.service.NumerologyDayService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/numerology")
@Tag(name = "Нумерология", description = "Личная нумерология пользователя")
public class NumerologyController extends BaseController {

    private final NumerologyDayService numerologyDayService;

    @GetMapping("/today")
    public NumerologyDayResponse getToday(HttpServletRequest request) {
        return numerologyDayService.getToday(resolveUser(request).getId());
    }
}
