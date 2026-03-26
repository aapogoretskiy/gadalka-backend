package ru.sapa.gadalka_backend.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sapa.gadalka_backend.api.dto.fortune.FortuneResponse;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.service.FortuneService;

@RestController
@RequestMapping("/api/fortune")
@RequiredArgsConstructor
@Tag(name = "Гадание", description = "Контроллер, отвечающий за кор функционал гадания")
public class FortuneController {

    private final FortuneService fortuneService;

    @GetMapping
    @Operation(summary = "Гадание \"3 карты\"")
    public FortuneResponse getFortune(HttpServletRequest request) {
        User user = (User) request.getAttribute("user");
        return fortuneService.getFortune(user);
    }
}
