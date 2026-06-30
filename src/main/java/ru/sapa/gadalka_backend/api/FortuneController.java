package ru.sapa.gadalka_backend.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sapa.gadalka_backend.api.dto.compatibility.CompatibilityRequest;
import ru.sapa.gadalka_backend.api.dto.compatibility.CompatibilityResponse;
import ru.sapa.gadalka_backend.api.dto.fortune.FortuneRequest;
import ru.sapa.gadalka_backend.api.dto.fortune.FortuneResponse;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.type.SensitiveContentCategory;
import ru.sapa.gadalka_backend.exception.SensitiveContentBlockedException;
import ru.sapa.gadalka_backend.service.AiRateLimitService;
import ru.sapa.gadalka_backend.service.CompatibilityService;
import ru.sapa.gadalka_backend.service.FortuneService;
import ru.sapa.gadalka_backend.service.ProfanityFilterService;
import ru.sapa.gadalka_backend.service.PromptInjectionFilterService;
import ru.sapa.gadalka_backend.service.SensitiveContentFilterService;

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/fortune")
@RequiredArgsConstructor
@Tag(name = "Гадание", description = "Контроллер, отвечающий за кор функционал гадания")
public class FortuneController extends BaseController {

    private final FortuneService fortuneService;
    private final CompatibilityService compatibilityService;
    private final ProfanityFilterService profanityFilterService;
    private final PromptInjectionFilterService promptInjectionFilterService;
    private final SensitiveContentFilterService sensitiveContentFilterService;
    private final AiRateLimitService aiRateLimitService;

    @PostMapping
    @Operation(summary = "Гадание Таро",
               description = "Возвращает одно и то же предсказание для одного пользователя и одного вопроса")
    public FortuneResponse getFortune(@Valid @RequestBody FortuneRequest fortuneRequest,
                                      HttpServletRequest request) {
        User user = resolveUser(request);
        profanityFilterService.validate(fortuneRequest.getQuestion());
        promptInjectionFilterService.validate(fortuneRequest.getQuestion(), user.getId());

        Optional<SensitiveContentCategory> sensitiveCategory = sensitiveContentFilterService.detectByKeywords(fortuneRequest.getQuestion());
        if (sensitiveCategory.isPresent()) {
            try {
                sensitiveContentFilterService.logSensitiveQuery(user.getId(), fortuneRequest.getQuestion(), sensitiveCategory.get());
            } catch (Exception logEx) {
                log.error("Не удалось залогировать чувствительный запрос (keyword): {}", logEx.getMessage());
            }
            throw new SensitiveContentBlockedException(sensitiveCategory.get());
        }

        aiRateLimitService.checkLimit(user.getId());
        return fortuneService.getFortune(user, fortuneRequest.getQuestion(),
                fortuneRequest.getCategory(), fortuneRequest.getSpreadType());
    }

    @PostMapping("/compatibility/{id}/unlock")
    @Operation(
            summary = "Разблокировать полный анализ совместимости",
            description = "Списывает 1 знак и возвращает полный анализ (интерпретацию и категории). " +
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
