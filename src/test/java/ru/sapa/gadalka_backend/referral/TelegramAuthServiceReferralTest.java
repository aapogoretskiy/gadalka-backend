package ru.sapa.gadalka_backend.referral;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.mapper.UserMapper;
import ru.sapa.gadalka_backend.repository.UserRepository;
import ru.sapa.gadalka_backend.service.JwtService;
import ru.sapa.gadalka_backend.service.ReferralService;
import ru.sapa.gadalka_backend.service.TelegramAuthService;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Тесты интеграции TelegramAuthService ↔ ReferralService.
 *
 * <p>Проверяем, что при наличии start_param в initData:
 * <ul>
 *   <li>вызывается {@code ReferralService.recordAppOpen()} с верными аргументами</li>
 *   <li>при отсутствии start_param реферальный сервис НЕ вызывается</li>
 * </ul>
 *
 * <p>Авторизация отключена ({@code authEnabled=false}), чтобы тест не зависел
 * от реального HMAC-ключа бота.
 */
@ExtendWith(MockitoExtension.class)
class TelegramAuthServiceReferralTest {

    @Mock private UserMapper userMapper;
    @Mock private JwtService jwtService;
    @Mock private UserRepository userRepository;
    @Mock private ReferralService referralService;

    private TelegramAuthService service;

    // initData с реальным user-JSON (без валидной подписи — authEnabled=false)
    private static final long TELEGRAM_ID = 123456789L;
    private static final String USER_JSON =
            "{\"id\":" + TELEGRAM_ID + ",\"first_name\":\"Test\",\"username\":\"testuser\"}";

    @BeforeEach
    void setUp() throws Exception {
        service = new TelegramAuthService(userMapper, jwtService, new ObjectMapper(),
                userRepository, referralService);
        setField(service, "botToken", "test_token");
        setField(service, "authEnabled", false);  // отключаем HMAC-проверку
    }

    /** Настраивает моки для сценария "новый пользователь". */
    private User stubNewUser() {
        User savedUser = User.builder().id(1L).telegramId(TELEGRAM_ID).build();
        when(userRepository.findByTelegramId(TELEGRAM_ID)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateToken(any())).thenReturn("jwt-token");
        when(userMapper.toDto(any())).thenReturn(null);
        return savedUser;
    }

    /** Настраивает моки для сценария "существующий пользователь". */
    private User stubExistingUser() {
        User existingUser = User.builder().id(2L).telegramId(TELEGRAM_ID).build();
        when(userRepository.findByTelegramId(TELEGRAM_ID)).thenReturn(Optional.of(existingUser));
        when(jwtService.generateToken(any())).thenReturn("jwt-token");
        when(userMapper.toDto(any())).thenReturn(null);
        return existingUser;
    }

    // ── С реферальным параметром ──────────────────────────────────────────────

    @Nested
    @DisplayName("initData содержит start_param")
    class WithStartParam {

        @Test
        @DisplayName("recordAppOpen вызывается для нового пользователя с верным кодом")
        void newUser_recordAppOpenCalled() {
            stubNewUser();

            service.authenticate(buildInitData("telegram_channel1"));

            verify(referralService).recordAppOpen(
                    eq(TELEGRAM_ID),
                    any(User.class),
                    eq(true),
                    eq("telegram_channel1")
            );
        }

        @Test
        @DisplayName("recordAppOpen вызывается для существующего пользователя с isNewUser=false")
        void existingUser_recordAppOpenCalledWithIsNewUserFalse() {
            User existingUser = stubExistingUser();

            service.authenticate(buildInitData("tiktok_video1"));

            verify(referralService).recordAppOpen(
                    eq(TELEGRAM_ID),
                    eq(existingUser),
                    eq(false),
                    eq("tiktok_video1")
            );
        }

        @Test
        @DisplayName("Разные источники передаются корректно")
        void differentSources_passedCorrectly() {
            String[] codes = {"telegram_channel1", "tiktok_video1", "instagram_bio", "vk_post_42"};

            for (String code : codes) {
                reset(referralService, userRepository, jwtService, userMapper);
                stubNewUser();

                service.authenticate(buildInitData(code));

                verify(referralService).recordAppOpen(anyLong(), any(), anyBoolean(), eq(code));
            }
        }
    }

    // ── Без реферального параметра ────────────────────────────────────────────

    @Nested
    @DisplayName("initData без start_param")
    class WithoutStartParam {

        @Test
        @DisplayName("Обычный вход без реферала: recordAppOpen НЕ вызывается")
        void noStartParam_referralServiceNotCalled() {
            stubNewUser();
            String initData = "user=" + urlEncode(USER_JSON) + "&auth_date=1700000000&hash=fake";

            service.authenticate(initData);

            verifyNoInteractions(referralService);
        }

        @Test
        @DisplayName("start_param пустая строка: recordAppOpen НЕ вызывается")
        void emptyStartParam_referralServiceNotCalled() {
            stubNewUser();
            String initData = "user=" + urlEncode(USER_JSON)
                    + "&start_param=&auth_date=1700000000&hash=fake";

            service.authenticate(initData);

            verifyNoInteractions(referralService);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildInitData(String startParam) {
        return "user=" + urlEncode(USER_JSON)
                + "&start_param=" + startParam
                + "&auth_date=1700000000"
                + "&hash=fakehash";
    }

    private String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
