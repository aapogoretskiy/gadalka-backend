package ru.sapa.gadalka_backend.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sapa.gadalka_backend.api.dto.card.DailyCardResponse;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.service.DailyCardService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/daily-card")
@Tag(name = "Карта дня", description = "Работа с картой дня")
public class DailyCardController {

    private final DailyCardService dailyCardService;

    @GetMapping
    public DailyCardResponse getDailyCard(HttpServletRequest request) {
        User user = (User) request.getAttribute("user");
        return dailyCardService.getDailyCard(user.getId());
    }
}
