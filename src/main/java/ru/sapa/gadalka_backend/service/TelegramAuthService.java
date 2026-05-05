package ru.sapa.gadalka_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.api.dto.telegram.TelegramAuthResponse;
import ru.sapa.gadalka_backend.api.dto.telegram.TelegramUserDto;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.mapper.UserMapper;
import ru.sapa.gadalka_backend.repository.UserRepository;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramAuthService {

    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final ReferralService referralService;
    private final FortuneCreditService fortuneCreditService;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.auth.enable:true}")
    private boolean authEnabled;

    public TelegramAuthResponse authenticate(String initData) {
        Map<String, String> data = parseInitData(initData);

        String receivedHash = data.remove("hash");

        if (authEnabled && !isValid(data, receivedHash)) {
            log.warn("Неверная подпись Telegram initData — аутентификация отклонена");
            throw new RuntimeException("Неверные данные Telegram аутентификации");
        }

        // Реферальный код приходит в поле start_param (если пользователь открыл Mini App
        // по ссылке вида https://t.me/bot?start=CODE или через startapp=CODE)
        String startParam = data.get("start_param");

        try {
            String userJson = data.get("user");
            log.debug("Разбор данных Telegram пользователя из initData");

            TelegramUserDto telegramUser = objectMapper.readValue(userJson, TelegramUserDto.class);
            log.debug("Telegram пользователь из initData: telegramId={}, username={}",
                    telegramUser.getId(), telegramUser.getUsername());

            boolean isNewUser = userRepository.findByTelegramId(telegramUser.getId()).isEmpty();
            User user = userRepository.findByTelegramId(telegramUser.getId())
                    .orElseGet(() -> userRepository.save(
                            User.builder()
                                    .telegramId(telegramUser.getId())
                                    .username(telegramUser.getUsername())
                                    .firstName(telegramUser.getFirstName())
                                    .lastName(telegramUser.getLastName())
                                    .build()
                    ));

            if (isNewUser) {
                log.info("Зарегистрирован новый пользователь: id={}, telegramId={}, username={}",
                        user.getId(), user.getTelegramId(), user.getUsername());
            } else {
                log.info("Повторный вход пользователя: id={}, telegramId={}, username={}",
                        user.getId(), user.getTelegramId(), user.getUsername());
            }

            // Фиксируем реферальное событие APP_OPEN (если пришли по реферальной ссылке)
            if (startParam != null && !startParam.isBlank()) {
                referralService.recordAppOpen(telegramUser.getId(), user, isNewUser, startParam);
            }

            String token = jwtService.generateToken(String.valueOf(user.getId()));
            log.debug("JWT токен выдан пользователю id={}", user.getId());
            return TelegramAuthResponse.builder()
                    .user(userMapper.toDto(user))
                    .jwtToken(token)
                    .readingBalance(fortuneCreditService.getBalance(user.getId()))
                    .build();

        } catch (Exception e) {
            log.error("Ошибка разбора JSON данных Telegram пользователя: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка разбора данных Telegram пользователя", e);
        }
    }

    private Map<String, String> parseInitData(String initData) {
        Map<String, String> map = new HashMap<>();

        String[] pairs = initData.split("&");

        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length < 2) {
                log.warn("Некорректная пара ключ=значение в initData: '{}'", pair);
                continue; // пропускаем невалидную пару вместо ArrayIndexOutOfBoundsException
            }
            String key = kv[0];
            String value = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            map.put(key, value);
        }

        return map;
    }

    private boolean isValid(Map<String, String> data, String receivedHash) {
        try {
            List<String> sorted = new ArrayList<>();
            data.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> sorted.add(e.getKey() + "=" + e.getValue()));

            String dataCheckString = String.join("\n", sorted);

            byte[] secretKey = hmacSha256("WebAppData".getBytes(StandardCharsets.UTF_8), botToken.getBytes(StandardCharsets.UTF_8));

            byte[] calculatedHash = hmacSha256(secretKey, dataCheckString.getBytes(StandardCharsets.UTF_8));

            String calculatedHex = bytesToHex(calculatedHash);

            // Используем constant-time сравнение для защиты от timing-атак
            return MessageDigest.isEqual(
                    calculatedHex.getBytes(StandardCharsets.UTF_8),
                    receivedHash.getBytes(StandardCharsets.UTF_8)
            );

        } catch (Exception e) {
            return false;
        }
    }

    private byte[] hmacSha256(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
