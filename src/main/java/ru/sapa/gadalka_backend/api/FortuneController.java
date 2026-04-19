package ru.sapa.gadalka_backend.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sapa.gadalka_backend.api.dto.fortune.FortuneResponse;
import ru.sapa.gadalka_backend.api.dto.fortune.FortuneThreeCardRequest;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.service.FortuneService;

@RestController
@RequestMapping("/api/fortune")
@RequiredArgsConstructor
@Tag(name = "Гадание", description = "Контроллер, отвечающий за кор функционал гадания")
public class FortuneController {

    private final FortuneService fortuneService;

    @PostMapping
    @Operation(summary = "Гадание \"3 карты\"")
    public FortuneResponse getFortune(HttpServletRequest request,
                                      @RequestBody FortuneThreeCardRequest fortuneThreeCardRequest) {
        User user = (User) request.getAttribute("user");
        return fortuneService.getFortune(user, fortuneThreeCardRequest.getQuestion());
    }
}
