package ru.sapa.gadalka_backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.sapa.gadalka_backend.service.ProfanityFilterService;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit-тесты фильтра нецензурной лексики.
 * Проверяем, что граница «разрешено / запрещено» работает корректно.
 */
class ProfanityFilterServiceTest {

    private ProfanityFilterService service;

    @BeforeEach
    void setUp() {
        service = new ProfanityFilterService();
    }

    @Test
    @DisplayName("Нормальный вопрос — должен пройти без исключений")
    void validate_normalQuestion_passes() {
        assertThatCode(() -> service.validate("Что ждёт меня в любви?"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Пустая строка — должна пройти без исключений")
    void validate_emptyString_passes() {
        assertThatCode(() -> service.validate(""))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("null — должен пройти без исключений")
    void validate_null_passes() {
        assertThatCode(() -> service.validate(null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Вопрос только с пробелами — должен пройти без исключений")
    void validate_whitespaceOnly_passes() {
        assertThatCode(() -> service.validate("   "))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Нецензурная лексика — должна бросить IllegalArgumentException")
    void validate_profanity_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.validate("хуй"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("уважения");
    }

    @Test
    @DisplayName("Нецензурная лексика в середине слова — должна фильтроваться")
    void validate_profanityInMiddle_throwsException() {
        assertThatThrownBy(() -> service.validate("Что-то пиздатое происходит"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Нецензурная лексика с заглавными буквами — должна фильтроваться")
    void validate_profanityUpperCase_throwsException() {
        assertThatThrownBy(() -> service.validate("ПИЗДА всему"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Очень длинный нормальный текст — должен пройти")
    void validate_longNormalText_passes() {
        String longText = "Что ждёт меня в ближайшем будущем? ".repeat(14); // ~490 символов
        assertThatCode(() -> service.validate(longText))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Текст на латинице без мата — должен пройти")
    void validate_latinText_passes() {
        assertThatCode(() -> service.validate("What does my future hold?"))
                .doesNotThrowAnyException();
    }
}
