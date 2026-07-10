package ru.sapa.gadalka_backend.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sapa.gadalka_backend.api.dto.dream.DreamHistoryItemDto;
import ru.sapa.gadalka_backend.api.dto.dream.DreamRequest;
import ru.sapa.gadalka_backend.api.dto.dream.DreamResponse;
import ru.sapa.gadalka_backend.api.dto.dream.DreamSymbolDto;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.type.DetectionSource;
import ru.sapa.gadalka_backend.exception.SensitiveContentBlockedException;
import ru.sapa.gadalka_backend.service.AiRateLimitService;
import ru.sapa.gadalka_backend.service.DreamService;
import ru.sapa.gadalka_backend.service.ProfanityFilterService;
import ru.sapa.gadalka_backend.service.PromptInjectionFilterService;
import ru.sapa.gadalka_backend.service.SensitiveContentFilterService;

import java.util.List;
import java.util.Optional;

/**
 * Сонник — платный AI-разбор снов.
 * Каскад фильтров на POST тот же, что у гаданий ({@link FortuneController}),
 * с одним отличием: keyword-фильтр работает в мягком режиме
 * ({@link SensitiveContentFilterService#detectByKeywordsForDream}) — образы смерти
 * и болезней в тексте сна допустимы, жёстко блокируются только СВО/политика/суицид.
 */
@Slf4j
@RestController
@RequestMapping("/api/dreams")
@RequiredArgsConstructor
@Tag(name = "Сонник", description = "AI-разбор снов с учётом знака зодиака и числа жизни")
public class DreamController extends BaseController {

    private final DreamService dreamService;
    private final ProfanityFilterService profanityFilterService;
    private final PromptInjectionFilterService promptInjectionFilterService;
    private final SensitiveContentFilterService sensitiveContentFilterService;
    private final AiRateLimitService aiRateLimitService;

    @GetMapping("/symbols")
    @Operation(summary = "Частые символы во снах",
               description = "Активные символы-чипы для экрана ввода, отсортированы по sort_order")
    public List<DreamSymbolDto> getSymbols() {
        return dreamService.getActiveSymbols();
    }

    @PostMapping
    @Operation(summary = "Разобрать сон",
               description = "Платный AI-разбор: списывает знаки по цене FEATURE_COST_DREAM после успешной генерации")
    public DreamResponse analyzeDream(@Valid @RequestBody DreamRequest dreamRequest,
                                      HttpServletRequest request) {
        User user = resolveUser(request);

        // Фильтры применяются только к свободному тексту — чипы выбираются из нашего же справочника
        if (StringUtils.isNotBlank(dreamRequest.getDreamText())) {
            profanityFilterService.validate(dreamRequest.getDreamText());
            promptInjectionFilterService.validate(dreamRequest.getDreamText(), user.getId());

            Optional<SensitiveContentFilterService.KeywordMatch> keywordMatch = sensitiveContentFilterService.detectByKeywordsForDreamWithMatch(dreamRequest.getDreamText());
            if (keywordMatch.isPresent()) {
                try {
                    sensitiveContentFilterService.logKeywordMatch(user.getId(), dreamRequest.getDreamText(), keywordMatch.get(), DetectionSource.KEYWORD);
                } catch (Exception logEx) {
                    log.error("Не удалось залогировать чувствительный сон (keyword): {}", logEx.getMessage());
                }
                throw new SensitiveContentBlockedException(keywordMatch.get().category());
            }
        }

        aiRateLimitService.checkLimit(user.getId());
        return dreamService.analyzeDream(user, dreamRequest);
    }

    @GetMapping("/recent")
    @Operation(summary = "Недавние сны", description = "Последние разборы для блока «Недавние сны» на экране Сонника")
    public List<DreamHistoryItemDto> getRecentDreams(HttpServletRequest request) {
        User user = resolveUser(request);
        return dreamService.getRecentDreams(user.getId());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Открыть сохранённый разбор сна", description = "Бесплатно: отдаёт снимок из истории, AI не вызывается")
    public DreamResponse getDream(@PathVariable Long id, HttpServletRequest request) {
        User user = resolveUser(request);
        return dreamService.getDream(user.getId(), id);
    }
}
