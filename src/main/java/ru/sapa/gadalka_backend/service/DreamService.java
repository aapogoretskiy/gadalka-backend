package ru.sapa.gadalka_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.sapa.gadalka_backend.api.dto.dream.DreamHistoryItemDto;
import ru.sapa.gadalka_backend.api.dto.dream.DreamRequest;
import ru.sapa.gadalka_backend.api.dto.dream.DreamResponse;
import ru.sapa.gadalka_backend.api.dto.dream.DreamSymbolDto;
import ru.sapa.gadalka_backend.domain.DreamReading;
import ru.sapa.gadalka_backend.domain.DreamSymbol;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.UserProfile;
import ru.sapa.gadalka_backend.domain.type.DetectionSource;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;
import ru.sapa.gadalka_backend.domain.type.SensitiveContentCategory;
import ru.sapa.gadalka_backend.domain.type.ZodiacSign;
import ru.sapa.gadalka_backend.exception.SensitiveContentBlockedException;
import ru.sapa.gadalka_backend.repository.DreamReadingRepository;
import ru.sapa.gadalka_backend.repository.DreamSymbolRepository;
import ru.sapa.gadalka_backend.repository.UserProfileRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;
import ru.sapa.gadalka_backend.service.interpretation.AiInterpretationManager;
import ru.sapa.gadalka_backend.service.interpretation.DreamContent;
import ru.sapa.gadalka_backend.service.interpretation.DreamGenerationException;
import ru.sapa.gadalka_backend.service.interpretation.DreamRefusedException;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import static ru.sapa.gadalka_backend.constant.SystemConfigConstants.AI_PROVIDER;

/**
 * Фича "Сонник" — платный AI-разбор сна с учётом знака зодиака и числа жизни.
 *
 * <p>Порядок операций в {@link #analyzeDream} важен (тот же принцип, что в {@link FortuneService}):
 * <ol>
 *   <li>валидация ввода и профиля — бесплатно;</li>
 *   <li>проверка наличия знаков ДО вызова AI — чтобы не тратить токены впустую;</li>
 *   <li>вызов AI;</li>
 *   <li>списание знаков ПОСЛЕ успешной генерации — при отказе AI или ошибке
 *       пользователь ничего не платит;</li>
 *   <li>сохранение снимка результата (payload) — повторное открытие из истории бесплатно.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DreamService {

    /** Сколько «недавних снов» показывать на экране Сонника. */
    private static final int RECENT_DREAMS_LIMIT = 5;

    private final DreamSymbolRepository dreamSymbolRepository;
    private final DreamReadingRepository dreamReadingRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;
    private final NumerologyService numerologyService;
    private final NumerologyContentService numerologyContentService;
    private final SystemConfigService systemConfigService;
    private final AiInterpretationManager interpretationManager;
    private final SensitiveContentFilterService sensitiveContentFilterService;
    private final FeatureSpendService featureSpendService;
    private final FeatureCostService featureCostService;
    private final DiaryService diaryService;
    private final ObjectMapper objectMapper;
    @Qualifier("aiTaskExecutor")
    private final Executor aiTaskExecutor;

    /** Активные символы-чипы для экрана ввода. */
    @Transactional(readOnly = true)
    public List<DreamSymbolDto> getActiveSymbols() {
        return dreamSymbolRepository.findByIsActiveTrueOrderBySortOrderAsc().stream()
                .map(s -> new DreamSymbolDto(s.getId(), s.getEmoji(), s.getName()))
                .toList();
    }

    @Transactional
    public DreamResponse analyzeDream(User user, DreamRequest request) {
        String dreamText = StringUtils.trimToNull(request.getDreamText());
        List<Long> symbolIds = request.getSymbolIds() != null ? request.getSymbolIds() : List.of();

        // Валидны: только текст, только символы, текст + символы. Пустой запрос — ошибка.
        if (dreamText == null && symbolIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Опишите сон или выберите хотя бы один символ");
        }

        // Контекст для AI (знак зодиака + число жизни) требует дату рождения —
        // поведение то же, что в Нумерологии и Гороскопе (фронт показывает экран заполнения профиля)
        UserProfile profile = userProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Для разбора сна необходимо указать дату рождения в профиле"));
        if (profile.getBirthDate() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Для разбора сна необходимо указать дату рождения в профиле");
        }

        // Проверяем знаки/квоту ДО вызова AI — чтобы не тратить токены впустую
        featureSpendService.assertSpendable(user.getId(), DiaryFeatureType.DREAM, featureCostService.getDreamCost(), request.getSpendMode());

        ZodiacSign zodiacSign = ZodiacSign.fromDate(profile.getBirthDate());
        int lifeNumber = numerologyService.lifePathNumber(profile.getBirthDate());

        // Выбранные чипы: имя + классическое значение (подсказка для промпта).
        // findAllById сохраняет только существующие ID — «мёртвые» ID из старого кэша фронта просто игнорируются.
        List<DreamSymbol> selectedSymbols = dreamSymbolRepository.findAllById(symbolIds);
        List<DreamContent.SymbolMeaning> symbolInputs = selectedSymbols.stream()
                .map(s -> new DreamContent.SymbolMeaning(s.getName(), s.getPromptHint()))
                .toList();

        String provider = systemConfigService.getValue(AI_PROVIDER);

        // Пре-чек нужен только для свободного текста — чипы-символы выбираются из нашего
        // же справочника, чувствительный вопрос там появиться не может по построению.
        // Запускаем параллельно с генерацией разбора (тот же принцип, что в FortuneService):
        // классификация — маленький вызов, почти всегда успевает раньше большой генерации.
        CompletableFuture<SensitiveContentFilterService.PreCheckResult> preCheckFuture = dreamText != null
                ? CompletableFuture.supplyAsync(
                        () -> sensitiveContentFilterService.classifyByLlmPreCheckForDream(dreamText), aiTaskExecutor)
                : CompletableFuture.completedFuture(
                        new SensitiveContentFilterService.PreCheckResult(SensitiveContentCategory.NOT_SENSITIVE, null));
        CompletableFuture<DreamContent> interpretationFuture = CompletableFuture.supplyAsync(
                () -> interpretationManager.interpretDream(provider, dreamText, symbolInputs, zodiacSign, lifeNumber), aiTaskExecutor);

        SensitiveContentFilterService.PreCheckResult preCheckResult = preCheckFuture.join();
        if (preCheckResult.isBlocked()) {
            sensitiveContentFilterService.logLlmDetection(user.getId(),
                    dreamText != null ? dreamText : "[только символы]",
                    preCheckResult.category(), DetectionSource.LLM_PRECHECK, preCheckResult.rawOutput());
            log.info("LLM pre-check заблокировал сон: userId={}, category={}", user.getId(), preCheckResult.category());
            throw new SensitiveContentBlockedException(preCheckResult.category());
        }

        DreamContent content;
        try {
            content = unwrapJoin(interpretationFuture);
        } catch (DreamRefusedException refused) {
            // Финальная страховка: keyword и pre-check пропустили, а сама генерация всё же отказала
            SensitiveContentCategory category = sensitiveContentFilterService.classifyByLlm(
                    dreamText != null ? dreamText : String.join(", ", symbolInputs.stream().map(DreamContent.SymbolMeaning::name).toList()));
            sensitiveContentFilterService.logLlmDetection(user.getId(),
                    dreamText != null ? dreamText : "[только символы]", category, DetectionSource.LLM_REFUSAL_FALLBACK, null);
            log.info("AI отказался разбирать сон: userId={}, category={}", user.getId(), category);
            throw new SensitiveContentBlockedException(category);
        } catch (DreamGenerationException genEx) {
            // AI не смог вернуть валидный JSON после всех ретраев — знаки не списаны
            log.error("Не удалось сгенерировать разбор сна: userId={}: {}", user.getId(), genEx.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Не удалось разобрать сон — попробуйте ещё раз чуть позже");
        }

        // Ответ валидный — списываем знаки или квоту (pessimistic lock внутри защищает от гонок)
        int cost = featureCostService.getDreamCost();
        featureSpendService.spend(user.getId(), DiaryFeatureType.DREAM, cost, request.getSpendMode());

        List<String> selectedSymbolNames = selectedSymbols.stream().map(DreamSymbol::getName).toList();

        DreamReading reading = DreamReading.builder()
                .userId(user.getId())
                .dreamText(dreamText)
                .selectedSymbols(serialize(selectedSymbolNames))
                .payload("{}") // временно — настоящий payload сериализуем ниже, когда известен id
                .build();
        reading = dreamReadingRepository.save(reading);

        DreamResponse response = new DreamResponse(
                reading.getId(),
                reading.getCreatedAt(),
                dreamText,
                selectedSymbolNames,
                content.titleSymbols(),
                content.mainMeaning(),
                lifeNumber,
                numerologyContentService.lifePathTitle(lifeNumber),
                content.lifeNumberSection(),
                zodiacSign.getDisplayName(),
                content.zodiacSection(),
                content.symbols().stream()
                        .map(s -> new DreamResponse.DreamSymbolMeaningDto(s.name(), s.meaning()))
                        .toList(),
                content.advice(),
                content.oracleQuestion()
        );

        reading.setPayload(serialize(response));
        userRepository.incrementActionsCount(user.getId());
        diaryService.save(user.getId(), DiaryFeatureType.DREAM, reading.getId(), response);

        log.info("Разбор сна создан и оплачен: userId={}, dreamReadingId={}, списано={} знаков",
                user.getId(), reading.getId(), cost);

        return response;
    }

    /** «Недавние сны» на экране Сонника. */
    @Transactional(readOnly = true)
    public List<DreamHistoryItemDto> getRecentDreams(Long userId) {
        return dreamReadingRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, RECENT_DREAMS_LIMIT))
                .stream()
                .map(r -> {
                    DreamResponse stored = deserialize(r.getPayload());
                    return new DreamHistoryItemDto(r.getId(), r.getCreatedAt(), stored.titleSymbols());
                })
                .toList();
    }

    /** Открытие сохранённого разбора из истории — бесплатно, AI не вызывается. */
    @Transactional(readOnly = true)
    public DreamResponse getDream(Long userId, Long dreamId) {
        DreamReading reading = dreamReadingRepository.findByIdAndUserId(dreamId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Разбор сна не найден"));
        return deserialize(reading.getPayload());
    }

    /**
     * {@code CompletableFuture.join()} оборачивает исключения из supplier'а в
     * {@link CompletionException}, теряя исходный тип — а {@link DreamRefusedException}/
     * {@link DreamGenerationException} должны доходить до catch-блоков выше как есть.
     */
    private <T> T unwrapJoin(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException ce) {
            if (ce.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw ce;
        }
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ошибка сериализации разбора сна", e);
        }
    }

    private DreamResponse deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, DreamResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ошибка чтения сохранённого разбора сна", e);
        }
    }
}
