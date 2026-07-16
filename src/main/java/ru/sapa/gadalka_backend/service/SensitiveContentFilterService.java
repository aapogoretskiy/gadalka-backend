package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.domain.SensitiveQueryLog;
import ru.sapa.gadalka_backend.domain.type.DetectionSource;
import ru.sapa.gadalka_backend.domain.type.SensitiveContentCategory;
import ru.sapa.gadalka_backend.repository.SensitiveQueryLogRepository;
import ru.sapa.gadalka_backend.service.interpretation.AiInterpretationManager;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static ru.sapa.gadalka_backend.constant.SystemConfigConstants.AI_PROVIDER;

/**
 * Многоуровневый фильтр чувствительного контента.
 *
 * <p>Уровень 1 — {@link #detectByKeywords}: мгновенно, бесплатно, без LLM.
 * Ловит очевидные случаи по ключевым корням.
 *
 * <p>Уровень 2 — {@link #classifyByLlmPreCheck}: LLM-классификатор ДО генерации
 * интерпретации (реальное время, запускается параллельно с генерацией). В отличие
 * от уровня 1 понимает семантику ("жив ли Владимир" реальный человек vs "жив ли кот
 * Владимир" питомец), с валидацией формата ответа и ретраями, fail-closed при провале.
 *
 * <p>Уровень 3 — {@link #isLlmRefusal}: анализирует уже сгенерированный ответ LLM
 * на паттерны отказа — страховка на случай, если первые два уровня всё-таки пропустили.
 *
 * <p>{@link #classifyByLlm} — вспомогательный дешёвый вызов только для того, чтобы
 * определить категорию уже точно отказанного вопроса (для логирования в уровне 3).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveContentFilterService {

    /** Ожидаемые слова в ответе LLM-классификатора: 9 категорий + NOT_SENSITIVE (без LLM_REFUSED/CLASSIFICATION_FAILED — это служебные значения, LLM их никогда не должна возвращать). */
    private static final Set<String> VALID_PRECHECK_LABELS = buildValidPrecheckLabels();

    private static final int PRECHECK_MAX_ATTEMPTS = 3;

    private static Set<String> buildValidPrecheckLabels() {
        Set<String> labels = new HashSet<>();
        for (SensitiveContentCategory category : SensitiveContentCategory.values()) {
            if (category == SensitiveContentCategory.LLM_REFUSED
                    || category == SensitiveContentCategory.CLASSIFICATION_FAILED) {
                continue;
            }
            labels.add(category.name());
        }
        return labels;
    }

    private final SensitiveQueryLogRepository sensitiveQueryLogRepository;
    private final AiInterpretationManager interpretationManager;
    private final SystemConfigService systemConfigService;
    private final SensitiveExplanationAsyncService explanationAsyncService;
    private final UserSensitivityProfileService userSensitivityProfileService;

    // ── Ключевые слова по категориям ───────────────────────────────────────
    // Нормализованные корни (будут нормализованы вместе с текстом пользователя).
    // LEGAL_FINANCIAL_ADVICE и LLM_REFUSED — контекстуальные, без keyword-фильтра.

    // Аббревиатуры и короткие слова, которые ищем ТОЛЬКО как отдельное слово.
    // Важно: "сво" как подстрока ложно матчит "своих", "своего", "свое" и т.д.,
    // поэтому вынесено сюда с проверкой по точному совпадению токена.
    private static final Map<SensitiveContentCategory, Set<String>> KEYWORD_EXACT_WORDS =
            Map.of(
                    SensitiveContentCategory.MILITARY_CONFLICT, Set.of("сво", "нато")
            );

    private static final Map<SensitiveContentCategory, Set<String>> KEYWORD_ROOTS =
            Map.ofEntries(
                    Map.entry(SensitiveContentCategory.MILITARY_CONFLICT, Set.of(
                            "спецоперац", "донбас", "луганск", "мобилиз",
                            "военнопленн", "укрфронт"
                    )),
                    Map.entry(SensitiveContentCategory.DEATH_MORTALITY, Set.of(
                            "умру", "умрет", "умереть",
                            "сколько проживу", "когда умру", "доживу ли",
                            "скоро умр", "дата смерти"
                    )),
                    Map.entry(SensitiveContentCategory.SELF_HARM_SUICIDE, Set.of(
                            "суицид", "самоубийств", "покончить с собой", "убить себя",
                            "повеситься", "прыгнуть с крыши", "вскрыть вен",
                            "причинить себе вред", "покончить жизнь"
                    )),
                    Map.entry(SensitiveContentCategory.HEALTH_MEDICAL, Set.of(
                            "диагноз поставят", "поставят диагноз", "вылечусь ли",
                            "вылечит ли", "химиотерап", "онкол", "опухол", "метастаз",
                            "вылечиться от рак", "выживу ли после операц"
                    )),
                    Map.entry(SensitiveContentCategory.CRIME_VIOLENCE, Set.of(
                            "убийств", "ограбл", "изнасилов", "маньяк", "педофил",
                            "терроризм", "взрыв устроить"
                    )),
                    Map.entry(SensitiveContentCategory.GAMBLING_INVESTMENT, Set.of(
                            "ставк на спорт", "лотерея выиграю"
                    )),
                    Map.entry(SensitiveContentCategory.POLITICAL_RELIGIOUS, Set.of(
                            "путин", "навальн", "кремл", "оппозиц"
                    )),
                    Map.entry(SensitiveContentCategory.MISSING_PERSONS_GUILT, Set.of(
                            "найти пропавш", "пропал человек", "кто убил", "кто виноват в убийстве",
                            "виновен ли", "где находится пропавш"
                    ))
            );

    // ── Паттерны отказа в ответах LLM ─────────────────────────────────────

    // (?iu), а не (?i): в Java флаг i делает регистронезависимым только ASCII —
    // без u паттерн "этот вопрос" не матчит "Этот вопрос" с заглавной буквы,
    // а LLM почти всегда начинает предложение с заглавной
    private static final List<Pattern> REFUSAL_PATTERNS = List.of(
            // "я" обязателен — иначе "он не может ответить себе" ловится как отказ LLM
            Pattern.compile("(?iu)(я не могу ответить|не в моих силах|я не буду отвечать)"),
            // "(это|этот вопрос) выходит за..." — LLM формулирует отказ и так, и так
            Pattern.compile("(?iu)((это|этот вопрос) выходит за (пределы|рамки)|этот вопрос (вне|за пределами))"),
            Pattern.compile("(?iu)(обратитесь к (специалисту|врачу|юристу|психологу|профессионалу))"),
            Pattern.compile("(?iu)(карты не могут (ответить|помочь) (на этот|с этим))"),
            Pattern.compile("(?iu)(я не могу (дать|предоставить) (профессиональн|медицинск|юридическ))")
    );

    private static final Pattern NORMALIZE_PATTERN = Pattern.compile("[^а-яa-z0-9 ]");

    /**
     * Проверяет вопрос по ключевым корням — до вызова LLM, бесплатно.
     *
     * <p>Сначала проверяет аббревиатуры/короткие слова из {@code KEYWORD_EXACT_WORDS}
     * по точному совпадению токена (чтобы "сво" не матчило "своих").
     * Затем проверяет корни из {@code KEYWORD_ROOTS} по вхождению подстроки.
     *
     * @return категория чувствительного контента, либо empty если вопрос безопасен
     */
    public Optional<SensitiveContentCategory> detectByKeywords(String question) {
        return detectByKeywordsWithMatch(question).map(KeywordMatch::category);
    }

    /**
     * То же самое, но вместе с категорией отдаёт и само сработавшее слово/корень —
     * используется для заполнения поля {@code explanation} в логе (детерминировано,
     * без обращения к LLM: для keyword-совпадения "почему" уже известно точно).
     */
    public Optional<KeywordMatch> detectByKeywordsWithMatch(String question) {
        return detectByKeywordsWithMatch(question, Set.of(SensitiveContentCategory.values()));
    }

    /**
     * Мягкий режим для Сонника: в тексте сна образы смерти, болезней и насилия — норма
     * (пользователь описывает сон, а не спрашивает о реальных событиях), поэтому
     * блокируем только категории, жёсткие для любого контекста: СВО/война, политика/религия
     * и суицид/самоповреждение (последнее — из соображений безопасности пользователя:
     * такой запрос требует ответа с телефоном доверия, а не мистической трактовки).
     */
    public Optional<SensitiveContentCategory> detectByKeywordsForDream(String dreamText) {
        return detectByKeywordsForDreamWithMatch(dreamText).map(KeywordMatch::category);
    }

    public Optional<KeywordMatch> detectByKeywordsForDreamWithMatch(String dreamText) {
        return detectByKeywordsWithMatch(dreamText, Set.of(
                SensitiveContentCategory.POLITICAL_RELIGIOUS,
                SensitiveContentCategory.SELF_HARM_SUICIDE));
    }

    /** Категория + конкретное сработавшее слово/корень (для explanation в логе) */
    public record KeywordMatch(SensitiveContentCategory category, String matchedTerm) {}

    /**
     * Общая реализация keyword-детекции с ограничением по набору категорий:
     * категории вне {@code categoriesToCheck} игнорируются целиком (их ключевые
     * слова даже не проверяются), а не отфильтровываются после первого совпадения —
     * иначе «безопасное» совпадение могло бы замаскировать «опасное».
     */
    private Optional<KeywordMatch> detectByKeywordsWithMatch(String question,
                                                              Set<SensitiveContentCategory> categoriesToCheck) {
        if (question == null || question.isBlank()) return Optional.empty();

        String normalized = normalize(question);

        // Уровень 1а: точное совпадение токена (аббревиатуры)
        for (Map.Entry<SensitiveContentCategory, Set<String>> entry : KEYWORD_EXACT_WORDS.entrySet()) {
            if (!categoriesToCheck.contains(entry.getKey())) continue;
            for (String word : entry.getValue()) {
                if (containsExactWord(normalized, normalize(word))) {
                    log.info("Keyword-фильтр (точное слово): '{}', категория={}", word, entry.getKey());
                    return Optional.of(new KeywordMatch(entry.getKey(), word));
                }
            }
        }

        // Уровень 1б: вхождение корня/подстроки
        for (Map.Entry<SensitiveContentCategory, Set<String>> entry : KEYWORD_ROOTS.entrySet()) {
            if (!categoriesToCheck.contains(entry.getKey())) continue;
            for (String root : entry.getValue()) {
                if (normalized.contains(normalize(root))) {
                    log.info("Keyword-фильтр (корень): '{}', категория={}", root, entry.getKey());
                    return Optional.of(new KeywordMatch(entry.getKey(), root));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Проверяет ответ LLM на паттерны отказа — бесплатно, без доп. токенов.
     *
     * @param response текст ответа LLM
     * @return true, если LLM отказал
     */
    public boolean isLlmRefusal(String response) {
        if (response == null || response.isBlank()) return false;

        for (Pattern pattern : REFUSAL_PATTERNS) {
            if (pattern.matcher(response).find()) {
                log.info("Обнаружен отказ LLM по паттерну: {}", pattern.pattern());
                return true;
            }
        }
        return false;
    }

    /**
     * Классифицирует вопрос через дешёвый LLM-вызов.
     * Вызывается только когда keyword-фильтр промахнулся, а LLM отказал.
     * Стоимость: ~200 входных + ~10 выходных токенов.
     */
    public SensitiveContentCategory classifyByLlm(String question) {
        try {
            String provider = systemConfigService.getValue(AI_PROVIDER);
            String raw = interpretationManager.classifySensitiveContent(provider, question);
            return parseCategory(raw);
        } catch (Exception e) {
            log.warn("Не удалось классифицировать вопрос через LLM: {}", e.getMessage());
            return SensitiveContentCategory.LLM_REFUSED;
        }
    }

    /** Результат pre-check классификации: категория (в т.ч. NOT_SENSITIVE/CLASSIFICATION_FAILED) + сырой ответ при провале формата */
    public record PreCheckResult(SensitiveContentCategory category, String rawOutput) {
        public boolean isBlocked() {
            return category != SensitiveContentCategory.NOT_SENSITIVE;
        }
    }

    /**
     * LLM-классификатор ДО генерации интерпретации — используется и в реальном времени
     * (параллельно с генерацией), и в бэкафилле истории.
     *
     * <p>Ответ модели строго валидируется против ожидаемых 10 слов (9 категорий +
     * NOT_SENSITIVE). При несовпадении — до {@value #PRECHECK_MAX_ATTEMPTS} попыток
     * (повторный вызов того же вопроса; LLM стохастична, чаще всего пересэмплирование
     * само чинит разовую формат-ошибку). Если после всех попыток формат так и не сошёлся —
     * fail-closed: возвращаем {@code CLASSIFICATION_FAILED} вместе с сырым текстом
     * последней попытки (для {@code raw_classification_output} в логе).
     */
    public PreCheckResult classifyByLlmPreCheck(String question) {
        String provider = systemConfigService.getValue(AI_PROVIDER);
        String lastRaw = null;

        for (int attempt = 1; attempt <= PRECHECK_MAX_ATTEMPTS; attempt++) {
            try {
                lastRaw = interpretationManager.classifyQuestionSensitivity(provider, question);
            } catch (Exception e) {
                log.warn("Ошибка вызова LLM pre-check (попытка {}/{}): {}", attempt, PRECHECK_MAX_ATTEMPTS, e.getMessage());
                lastRaw = null;
                continue;
            }

            String normalized = normalizeLabel(lastRaw);
            if (VALID_PRECHECK_LABELS.contains(normalized)) {
                return new PreCheckResult(SensitiveContentCategory.valueOf(normalized), null);
            }
            log.warn("LLM pre-check вернул не ожидаемый формат (попытка {}/{}): '{}'",
                    attempt, PRECHECK_MAX_ATTEMPTS, lastRaw);
        }

        log.error("LLM pre-check не вернул валидный формат после {} попыток — fail-closed. Сырой ответ: '{}'",
                PRECHECK_MAX_ATTEMPTS, lastRaw);
        return new PreCheckResult(SensitiveContentCategory.CLASSIFICATION_FAILED, lastRaw);
    }

    private String normalizeLabel(String raw) {
        if (raw == null) return "";
        return raw.trim().toUpperCase().replaceAll("[^A-Z_]", "");
    }

    /**
     * Pre-check для Сонника: та же LLM-классификация, что и {@link #classifyByLlmPreCheck},
     * но результат интерпретируется в мягком режиме — так же, как {@link #detectByKeywordsForDream}
     * работает поверх той же карты ключевых слов, что и обычный {@link #detectByKeywords}.
     * Образы смерти/болезни/насилия в описании сна — норма, поэтому категории вроде
     * DEATH_MORTALITY или HEALTH_MEDICAL от классификатора здесь НЕ блокируют; жёстко
     * блокируются только POLITICAL_RELIGIOUS и SELF_HARM_SUICIDE (и CLASSIFICATION_FAILED —
     * fail-closed остаётся fail-closed независимо от контекста).
     */
    public PreCheckResult classifyByLlmPreCheckForDream(String dreamText) {
        PreCheckResult raw = classifyByLlmPreCheck(dreamText);
        if (raw.category() == SensitiveContentCategory.CLASSIFICATION_FAILED) {
            return raw;
        }
        boolean blocksInDreamContext = raw.category() == SensitiveContentCategory.POLITICAL_RELIGIOUS
                || raw.category() == SensitiveContentCategory.SELF_HARM_SUICIDE;
        return blocksInDreamContext ? raw : new PreCheckResult(SensitiveContentCategory.NOT_SENSITIVE, null);
    }

    /**
     * Логирует чувствительный запрос в БД.
     * Выполняется в <b>отдельной транзакции</b> — запись фиксируется независимо
     * от того, откатится ли внешняя транзакция (например, при броске исключения).
     *
     * @return сохранённая запись (нужен id — для асинхронного дозаполнения explanation)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SensitiveQueryLog logSensitiveQuery(Long userId, String question, SensitiveContentCategory category,
                                                DetectionSource source, String rawClassificationOutput, String explanation) {
        SensitiveQueryLog saved = sensitiveQueryLogRepository.save(SensitiveQueryLog.builder()
                .userId(userId)
                .question(question)
                .category(category)
                .source(source)
                .rawClassificationOutput(rawClassificationOutput)
                .explanation(explanation)
                .build());
        log.info("Залогирован чувствительный запрос: userId={}, category={}, source={}", userId, category, source);
        return saved;
    }

    /** Keyword-источник: explanation уже известен детерминированно (сработавшее слово), LLM не вызываем. */
    public SensitiveQueryLog logKeywordMatch(Long userId, String question, KeywordMatch match, DetectionSource source) {
        SensitiveQueryLog saved = logSensitiveQuery(userId, question, match.category(), source, null,
                "Сработало ключевое слово: \"" + match.matchedTerm() + "\"");
        userSensitivityProfileService.recomputeProfile(userId);
        return saved;
    }

    /**
     * Запись при бэкафилле — то же самое, но с явным {@code detectedAt}, выставленным
     * в дату исходного вопроса (не "сейчас"): иначе вся история в админке выглядела бы
     * так, будто все старые вопросы обнаружены сегодня.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SensitiveQueryLog logBackfillEntry(Long userId, String question, SensitiveContentCategory category,
                                               DetectionSource source, String rawClassificationOutput,
                                               String explanation, java.time.OffsetDateTime detectedAt) {
        SensitiveQueryLog saved = sensitiveQueryLogRepository.save(SensitiveQueryLog.builder()
                .userId(userId)
                .question(question)
                .category(category)
                .source(source)
                .rawClassificationOutput(rawClassificationOutput)
                .explanation(explanation)
                .detectedAt(detectedAt)
                .build());
        log.info("Бэкафилл: залогирован исторический чувствительный запрос: userId={}, category={}, source={}",
                userId, category, source);
        return saved;
    }

    /**
     * Синхронное объяснение для бэкафилла — в отличие от реального времени тут нет
     * живого пользователя, которого нельзя тормозить, поэтому асинхронность не нужна.
     */
    public String explainForBackfill(String question, SensitiveContentCategory category) {
        try {
            String provider = systemConfigService.getValue(AI_PROVIDER);
            return interpretationManager.explainSensitiveClassification(provider, question, category.name());
        } catch (Exception e) {
            log.warn("Не удалось получить объяснение при бэкафилле: {}", e.getMessage());
            return null;
        }
    }

    /**
     * LLM-источник (pre-check в реальном времени или отказ на генерации) — логируем сразу,
     * а объяснение для админки дозаполняем асинхронно, чтобы не тормозить ответ пользователю.
     * Для CLASSIFICATION_FAILED объяснение не запрашиваем — там дебажный сигнал уже есть
     * в rawClassificationOutput, а спрашивать LLM "почему ты не смогла ответить в формате"
     * бессмысленно и ненадёжно.
     */
    public SensitiveQueryLog logLlmDetection(Long userId, String question, SensitiveContentCategory category,
                                              DetectionSource source, String rawClassificationOutput) {
        SensitiveQueryLog saved = logSensitiveQuery(userId, question, category, source, rawClassificationOutput, null);
        if (category != SensitiveContentCategory.CLASSIFICATION_FAILED) {
            explanationAsyncService.fetchAndAttachExplanationAsync(saved.getId(), question, category);
        }
        userSensitivityProfileService.recomputeProfile(userId);
        return saved;
    }

    /**
     * Проверяет, содержит ли нормализованный текст слово {@code word} как отдельный токен.
     * Токены разделяются пробелами (текст уже нормализован, знаки препинания удалены).
     */
    private boolean containsExactWord(String normalizedText, String word) {
        for (String token : normalizedText.split(" +")) {
            if (token.equals(word)) return true;
        }
        return false;
    }

    private String normalize(String text) {
        return NORMALIZE_PATTERN
                .matcher(text.toLowerCase().replace('ё', 'е'))
                .replaceAll("");
    }

    private SensitiveContentCategory parseCategory(String raw) {
        if (raw == null) return SensitiveContentCategory.LLM_REFUSED;
        try {
            return SensitiveContentCategory.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("LLM вернул неизвестную категорию: '{}', используем LLM_REFUSED", raw);
            return SensitiveContentCategory.LLM_REFUSED;
        }
    }
}
