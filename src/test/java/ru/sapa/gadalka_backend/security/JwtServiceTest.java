package ru.sapa.gadalka_backend.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.sapa.gadalka_backend.service.JwtService;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit-тесты безопасности JwtService.
 * Не требуют Spring-контекста — работают с чистым POJO.
 */
class JwtServiceTest {

    // 256-битный секрет (32 байта base-16) — минимум для HMAC-SHA256
    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET);
    }

    @Test
    @DisplayName("Генерация и чтение userId: round-trip должен вернуть тот же ID")
    void generateAndParse_roundTrip() {
        String token = jwtService.generateToken("42");

        Long userId = jwtService.getUserIdFromToken(token);

        assertThat(userId).isEqualTo(42L);
    }

    @Test
    @DisplayName("Невалидная подпись — должен бросить исключение")
    void invalidSignature_throwsException() {
        JwtService anotherService = new JwtService("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
        String tokenFromAnother = anotherService.generateToken("99");

        // Пытаемся распарсить токен с чужой подписью
        assertThatThrownBy(() -> jwtService.getUserIdFromToken(tokenFromAnother))
                .isInstanceOf(Exception.class); // SignatureException или JwtException
    }

    @Test
    @DisplayName("Мусорный токен — должен бросить исключение")
    void garbageToken_throwsException() {
        assertThatThrownBy(() -> jwtService.getUserIdFromToken("not.a.jwt.token"))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Пустая строка вместо токена — должен бросить исключение")
    void emptyToken_throwsException() {
        assertThatThrownBy(() -> jwtService.getUserIdFromToken(""))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Токен содержит корректный userId в subject")
    void token_containsCorrectSubject() {
        String token = jwtService.generateToken("777");

        Long userId = jwtService.getUserIdFromToken(token);

        assertThat(userId).isEqualTo(777L);
    }

    @Test
    @DisplayName("Разные userId порождают разные токены")
    void differentUsers_differentTokens() {
        String token1 = jwtService.generateToken("1");
        String token2 = jwtService.generateToken("2");

        assertThat(token1).isNotEqualTo(token2);
    }
}
