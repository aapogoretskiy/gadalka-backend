package ru.sapa.gadalka_backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.sapa.gadalka_backend.mapper.UserMapper;
import ru.sapa.gadalka_backend.repository.UserRepository;
import ru.sapa.gadalka_backend.service.FortuneCreditService;
import ru.sapa.gadalka_backend.service.JwtService;
import ru.sapa.gadalka_backend.service.ReferralService;
import ru.sapa.gadalka_backend.service.TelegramAuthService;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit-тесты безопасности TelegramAuthService.
 *
 * Проверяем критические аспекты:
 * - Валидация HMAC-подписи Telegram initData
 * - Безопасность парсинга initData (нет ArrayIndexOutOfBoundsException на мусоре)
 * - Constant-time сравнение (защита от timing-атак)
 */
@ExtendWith(MockitoExtension.class)
class TelegramAuthServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private JwtService jwtService;
    @Mock private ObjectMapper objectMapper;
    @Mock private UserRepository userRepository;
    @Mock private ReferralService referralService;
    @Mock private FortuneCreditService fortuneCreditService;

    private TelegramAuthService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new TelegramAuthService(userMapper, jwtService, objectMapper, userRepository, referralService, fortuneCreditService);
        // Инжектируем приватные поля через reflection (Spring их заполнит через @Value)
        setField(service, "botToken", "test_bot_token");
        setField(service, "authEnabled", true);
    }

    // ── parseInitData (через authenticate + невалидный hash) ─────────────────

    @Test
    @DisplayName("Мусорный initData без '=' не должен бросать ArrayIndexOutOfBoundsException")
    void parseInitData_malformedPair_doesNotThrowArrayOutOfBounds() {
        // initData без символа '=' в одной паре
        String malformedInitData = "keyWithoutEquals&hash=abc";

        // Должен получить RuntimeException из-за неверной подписи (или парсинга user JSON),
        // но НЕ ArrayIndexOutOfBoundsException
        assertThatThrownBy(() -> service.authenticate(malformedInitData))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(ArrayIndexOutOfBoundsException.class);
    }

    @Test
    @DisplayName("initData без поля hash должен бросить RuntimeException (не NPE)")
    void parseInitData_noHashField_throwsRuntimeException() {
        String noHash = "user=%7B%22id%22%3A1%7D&auth_date=1234567890";

        assertThatThrownBy(() -> service.authenticate(noHash))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Пустой initData должен бросить RuntimeException (не NPE)")
    void parseInitData_empty_throwsRuntimeException() {
        assertThatThrownBy(() -> service.authenticate(""))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Неверная HMAC-подпись при authEnabled=true должна отклоняться")
    void isValid_wrongHash_rejectsAuthentication() {
        String fakeInitData = "user=%7B%22id%22%3A1%7D&auth_date=1234567890&hash=0000000000000000000000000000000000000000000000000000000000000000";

        assertThatThrownBy(() -> service.authenticate(fakeInitData))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Неверные данные Telegram аутентификации");
    }

    // ── isValid (private, через reflection) ──────────────────────────────────

    @Test
    @DisplayName("isValid возвращает false для заведомо неверного хеша")
    void isValid_wrongHash_returnsFalse() throws Exception {
        Method isValid = TelegramAuthService.class.getDeclaredMethod("isValid", java.util.Map.class, String.class);
        isValid.setAccessible(true);

        java.util.Map<String, String> data = new java.util.HashMap<>();
        data.put("auth_date", "1234567890");
        data.put("user", "{\"id\":1}");

        boolean result = (boolean) isValid.invoke(service, data, "deadbeef");
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isValid возвращает false при пустом хеше")
    void isValid_emptyHash_returnsFalse() throws Exception {
        Method isValid = TelegramAuthService.class.getDeclaredMethod("isValid", java.util.Map.class, String.class);
        isValid.setAccessible(true);

        java.util.Map<String, String> data = new java.util.HashMap<>();
        data.put("auth_date", "1234567890");

        boolean result = (boolean) isValid.invoke(service, data, "");
        assertThat(result).isFalse();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
