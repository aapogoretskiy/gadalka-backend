package ru.sapa.gadalka_backend.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sapa.gadalka_backend.api.dto.compatibility.CompatibilityRequest;
import ru.sapa.gadalka_backend.api.dto.compatibility.CompatibilityResponse;
import ru.sapa.gadalka_backend.api.dto.fortune.FortuneRequest;
import ru.sapa.gadalka_backend.api.dto.fortune.FortuneResponse;
import ru.sapa.gadalka_backend.service.CompatibilityService;
import ru.sapa.gadalka_backend.service.FortuneService;
import ru.sapa.gadalka_backend.service.ProfanityFilterService;

@RestController
@RequestMapping("/api/fortune")
@RequiredArgsConstructor
@Tag(name = "Гадание", description = "Контроллер, отвечающий за кор функционал гадания")
public class FortuneController extends BaseController {

    private final FortuneService fortuneService;
    private final CompatibilityService compatibilityService;
    private final ProfanityFilterService profanityFilterService;

    @PostMapping
    @Operation(summary = "Гадание \"3 карты\"",
               description = "Возвращает одно и то же предсказание для одного пользователя и одного вопроса")
    public FortuneResponse getFortune(@Valid @RequestBody FortuneRequest fortuneRequest,
                                      HttpServletRequest request) {
        profanityFilterService.validate(fortuneRequest.getQuestion());
        return fortuneService.getFortune(resolveUser(request), fortuneRequest.getQuestion(), fortuneRequest.getCategory());
    }

    @PostMapping("/compatibility/{id}/unlock")
    @Operation(
            summary = "Разблокировать полный анализ совместимости",
            description = "Списывает 1 гадание и возвращает полный анализ (интерпретацию и категории). " +
                          "Повторный вызов для уже разблокированного расклада — бесплатен.")
    public CompatibilityResponse unlockCompatibility(@PathVariable Long id,
                                                     HttpServletRequest request) {
        return compatibilityService.unlockCompatibility(id, resolveUser(request));
    }

    @PostMapping("/compatibility")
    @Operation(
            summary = "Совместимость",
            description = """
                    Анализирует совместимость двух людей по нумерологическим правилам.
                    Возвращает один и тот же расклад для одного и того же пользователя и одной и той же пары (идемпотентность).
                    Порядок участников в запросе не влияет на результат.

                    **Важно об именах:** для стабильного результата используйте полные официальные имена
                    (например, «Александр», а не «Саша»). Краткие и полные формы одного имени
                    считаются разными людьми и дадут разные расклады.
                    """)
    public CompatibilityResponse getCompatibility(@Valid @RequestBody CompatibilityRequest compatibilityRequest,
                                                  HttpServletRequest request) {
        return compatibilityService.getCompatibility(resolveUser(request), compatibilityRequest.getPersons());
    }
}
