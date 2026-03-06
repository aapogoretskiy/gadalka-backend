package ru.sapa.gadalka_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.api.dto.TelegramAuthResponse;
import ru.sapa.gadalka_backend.api.dto.TelegramUserDto;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.mapper.UserMapper;
import ru.sapa.gadalka_backend.repository.UserRepository;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.auth.enable}")
    private boolean authEnabled;

    public TelegramAuthResponse authenticate(String initData) {
        Map<String, String> data = parseInitData(initData);

        String receivedHash = data.remove("hash");

        if (authEnabled && !isValid(data, receivedHash)) {
            throw new RuntimeException("Invalid Telegram auth data");
        }

        try {
            String userJson = data.get("user");

            TelegramUserDto telegramUser = objectMapper.readValue(userJson, TelegramUserDto.class);

            User user = userRepository.findByTelegramId(telegramUser.getId())
                    .orElseGet(() -> userRepository.save(
                            User.builder()
                                    .telegramId(telegramUser.getId())
                                    .username(telegramUser.getUsername())
                                    .firstName(telegramUser.getFirstName())
                                    .lastName(telegramUser.getLastName())
                                    .build()
                    ));

            log.info("Authenticated telegram user: id={}, telegramId={}",
                    user.getId(), user.getTelegramId());

            String token = jwtService.generateToken(String.valueOf(user.getId()));
            return TelegramAuthResponse.builder()
                    .user(userMapper.toDto(user))
                    .jwtToken(token)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Telegram user JSON", e);
        }
    }

    private Map<String, String> parseInitData(String initData) {
        Map<String, String> map = new HashMap<>();

        String[] pairs = initData.split("&");

        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
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

            return calculatedHex.equals(receivedHash);

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
