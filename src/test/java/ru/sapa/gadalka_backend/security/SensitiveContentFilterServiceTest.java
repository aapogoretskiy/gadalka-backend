package ru.sapa.gadalka_backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.sapa.gadalka_backend.domain.type.SensitiveContentCategory;
import ru.sapa.gadalka_backend.repository.SensitiveQueryLogRepository;
import ru.sapa.gadalka_backend.service.SensitiveContentFilterService;
import ru.sapa.gadalka_backend.service.SensitiveExplanationAsyncService;
import ru.sapa.gadalka_backend.service.SystemConfigService;
import ru.sapa.gadalka_backend.service.UserSensitivityProfileService;
import ru.sapa.gadalka_backend.service.interpretation.AiInterpretationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты pre-check классификатора: валидация формата ответа LLM, ретраи,
 * fail-closed, и мягкий режим для Сонника (см. историю обсуждения фичи "рейтинг
 * склонности к чувствительным вопросам" — конкретно кейс "жив ли Владимир" vs
 * "жив ли кот Владимир", а также сбои формата ответа модели).
 */
@ExtendWith(MockitoExtension.class)
class SensitiveContentFilterServiceTest {

    @Mock private SensitiveQueryLogRepository sensitiveQueryLogRepository;
    @Mock private AiInterpretationManager interpretationManager;
    @Mock private SystemConfigService systemConfigService;
    @Mock private SensitiveExplanationAsyncService explanationAsyncService;
    @Mock private UserSensitivityProfileService userSensitivityProfileService;

    private SensitiveContentFilterService service;

    @BeforeEach
    void setUp() {
        service = new SensitiveContentFilterService(
                sensitiveQueryLogRepository, interpretationManager, systemConfigService,
                explanationAsyncService, userSensitivityProfileService);
        // lenient: стаб нужен только тестам, доходящим до LLM-вызова (classifyByLlm*);
        // keyword- и refusal-тесты его не трогают — без lenient строгий Mockito
        // валит их с UnnecessaryStubbingException
        lenient().when(systemConfigService.getValue(anyString())).thenReturn("mock");
    }

    // ── Keyword-детекция ────────────────────────────────────────────────────

    @Test
    @DisplayName("Keyword-фильтр находит корень и возвращает сработавшее слово")
    void detectByKeywordsWithMatch_findsRoot() {
        var match = service.detectByKeywordsWithMatch("Когда умру и что меня ждёт после?");
        assertThat(match).isPresent();
        assertThat(match.get().category()).isEqualTo(SensitiveContentCategory.DEATH_MORTALITY);
    }

    @Test
    @DisplayName("Обычный вопрос не матчится keyword-фильтром")
    void detectByKeywordsWithMatch_safeQuestion_empty() {
        assertThat(service.detectByKeywordsWithMatch("Стоит ли мне сменить работу в этом году?")).isEmpty();
    }

    @Test
    @DisplayName("Keyword-фильтр НЕ ловит 'жив ли Владимир' — этот пробел и закрывает LLM pre-check")
    void detectByKeywordsWithMatch_missesThirdPersonAliveQuestion() {
        assertThat(service.detectByKeywordsWithMatch("Жив ли Владимир 19.11.1970 года рождения?")).isEmpty();
    }

    // ── LLM pre-check: happy path ────────────────────────────────────────────

    @Test
    @DisplayName("Валидный ответ NOT_SENSITIVE с первой попытки — не блокирует, LLM вызывается один раз")
    void classifyByLlmPreCheck_notSensitive_singleCall() {
        when(interpretationManager.classifyQuestionSensitivity(any(), any())).thenReturn("NOT_SENSITIVE");

        var result = service.classifyByLlmPreCheck("Жив ли мой кот Владимир, ему 15 лет?");

        assertThat(result.category()).isEqualTo(SensitiveContentCategory.NOT_SENSITIVE);
        assertThat(result.isBlocked()).isFalse();
        verify(interpretationManager, times(1)).classifyQuestionSensitivity(any(), any());
    }

    @Test
    @DisplayName("Валидная категория блокирует вопрос")
    void classifyByLlmPreCheck_realCategory_blocks() {
        when(interpretationManager.classifyQuestionSensitivity(any(), any())).thenReturn("DEATH_MORTALITY");

        var result = service.classifyByLlmPreCheck("Жив ли Владимир 19.11.1970 года рождения?");

        assertThat(result.category()).isEqualTo(SensitiveContentCategory.DEATH_MORTALITY);
        assertThat(result.isBlocked()).isTrue();
    }

    // ── LLM pre-check: валидация формата и ретраи ───────────────────────────

    @Test
    @DisplayName("Неожиданный формат ответа — ретраи, третья попытка валидна")
    void classifyByLlmPreCheck_retriesUntilValid() {
        // Нормализация обрезает всё, кроме [A-Z_] — поэтому для проверки ретраев нужны
        // значения, которые НЕ схлопываются в валидное слово даже после этой обрезки
        // (например "не знаю" превратится в "" — это невалидно, а вот "поясню: DEATH_MORTALITY"
        // ошибочно схлопнулось бы в валидное "DEATHMORTALITY"-подобное значение, если бы
        // в пояснении встретились латинские буквы — поэтому берём чисто кириллический мусор).
        when(interpretationManager.classifyQuestionSensitivity(any(), any()))
                .thenReturn("не знаю")   // 1: строка целиком на кириллице → нормализуется в "" → невалидно
                .thenReturn("может быть") // 2: снова кириллица → "" → невалидно
                .thenReturn("DEATH_MORTALITY"); // 3: валидно

        var result = service.classifyByLlmPreCheck("Вопрос");

        assertThat(result.category()).isEqualTo(SensitiveContentCategory.DEATH_MORTALITY);
        verify(interpretationManager, times(3)).classifyQuestionSensitivity(any(), any());
    }

    @Test
    @DisplayName("Формат так и не сошёлся за все попытки — fail-closed, CLASSIFICATION_FAILED с сырым текстом")
    void classifyByLlmPreCheck_neverValid_failsClosed() {
        when(interpretationManager.classifyQuestionSensitivity(any(), any()))
                .thenReturn("что-то невразумительное");

        var result = service.classifyByLlmPreCheck("Вопрос");

        assertThat(result.category()).isEqualTo(SensitiveContentCategory.CLASSIFICATION_FAILED);
        assertThat(result.rawOutput()).isEqualTo("что-то невразумительное");
        assertThat(result.isBlocked()).isTrue(); // fail-closed = блокируем
        verify(interpretationManager, times(3)).classifyQuestionSensitivity(any(), any());
    }

    @Test
    @DisplayName("LLM_REFUSED и CLASSIFICATION_FAILED — служебные значения, сама модель их вернуть не должна: считаются невалидным форматом")
    void classifyByLlmPreCheck_serviceValuesAreNotValidLlmOutput() {
        when(interpretationManager.classifyQuestionSensitivity(any(), any()))
                .thenReturn("LLM_REFUSED");

        var result = service.classifyByLlmPreCheck("Вопрос");

        assertThat(result.category()).isEqualTo(SensitiveContentCategory.CLASSIFICATION_FAILED);
    }

    @Test
    @DisplayName("Исключение при вызове LLM тоже считается неудачной попыткой, а не падением метода")
    void classifyByLlmPreCheck_exceptionDuringCall_treatedAsFailedAttempt() {
        when(interpretationManager.classifyQuestionSensitivity(any(), any()))
                .thenThrow(new RuntimeException("сеть недоступна"))
                .thenReturn("NOT_SENSITIVE");

        var result = service.classifyByLlmPreCheck("Вопрос");

        assertThat(result.category()).isEqualTo(SensitiveContentCategory.NOT_SENSITIVE);
        verify(interpretationManager, times(2)).classifyQuestionSensitivity(any(), any());
    }

    // ── Мягкий режим для Сонника ─────────────────────────────────────────────

    @Test
    @DisplayName("Сонник: DEATH_MORTALITY от классификатора не блокирует — образы смерти во сне норма")
    void classifyByLlmPreCheckForDream_deathMortality_notBlocked() {
        when(interpretationManager.classifyQuestionSensitivity(any(), any())).thenReturn("DEATH_MORTALITY");

        var result = service.classifyByLlmPreCheckForDream("Мне снилось, что умер мой дед");

        assertThat(result.category()).isEqualTo(SensitiveContentCategory.NOT_SENSITIVE);
        assertThat(result.isBlocked()).isFalse();
    }

    @Test
    @DisplayName("Сонник: SELF_HARM_SUICIDE от классификатора блокирует даже в контексте сна")
    void classifyByLlmPreCheckForDream_selfHarm_blocked() {
        when(interpretationManager.classifyQuestionSensitivity(any(), any())).thenReturn("SELF_HARM_SUICIDE");

        var result = service.classifyByLlmPreCheckForDream("Во сне я хотел причинить себе вред, и наяву тоже думаю об этом");

        assertThat(result.category()).isEqualTo(SensitiveContentCategory.SELF_HARM_SUICIDE);
        assertThat(result.isBlocked()).isTrue();
    }

    @Test
    @DisplayName("Сонник: fail-closed остаётся fail-closed независимо от мягкого режима")
    void classifyByLlmPreCheckForDream_classificationFailed_stillBlocked() {
        when(interpretationManager.classifyQuestionSensitivity(any(), any())).thenReturn("абракадабра");

        var result = service.classifyByLlmPreCheckForDream("Текст сна");

        assertThat(result.category()).isEqualTo(SensitiveContentCategory.CLASSIFICATION_FAILED);
        assertThat(result.isBlocked()).isTrue();
    }

    // ── Отказ LLM по паттернам (уровень 3, страховка) ───────────────────────

    @Test
    @DisplayName("Явная фраза отказа распознаётся")
    void isLlmRefusal_detectsRefusalPhrase() {
        assertThat(service.isLlmRefusal("Этот вопрос выходит за пределы того, о чём могут говорить карты."))
                .isTrue();
    }

    @Test
    @DisplayName("Обычная интерпретация не считается отказом")
    void isLlmRefusal_normalInterpretation_notRefusal() {
        assertThat(service.isLlmRefusal("Карты указывают на важные перемены в вашей жизни."))
                .isFalse();
    }
}
