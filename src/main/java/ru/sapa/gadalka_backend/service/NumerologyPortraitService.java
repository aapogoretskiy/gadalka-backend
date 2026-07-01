package ru.sapa.gadalka_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.sapa.gadalka_backend.api.dto.numerology.NumerologyPortraitCompatibilityItem;
import ru.sapa.gadalka_backend.api.dto.numerology.NumerologyPortraitResponse;
import ru.sapa.gadalka_backend.domain.DiaryEntry;
import ru.sapa.gadalka_backend.domain.UserProfile;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;
import ru.sapa.gadalka_backend.repository.DiaryRepository;
import ru.sapa.gadalka_backend.repository.UserProfileRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class NumerologyPortraitService {

    private final NumerologyService numerologyService;
    private final NumerologyContentService contentService;
    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;
    private final DiaryRepository diaryRepository;
    private final ObjectMapper objectMapper;

    public NumerologyPortraitResponse getPortrait(Long userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Для расчёта портрета необходимо указать дату рождения в профиле"));

        if (profile.getBirthDate() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Для расчёта портрета необходимо указать дату рождения в профиле");
        }

        // Определяем имя для расчётов: пользовательское или из Telegram
        String customName = profile.getNumerologyName();
        String nameUsed;
        String nameSource;

        if (customName != null && !customName.isBlank()) {
            nameUsed   = customName.trim();
            nameSource = "custom";
        } else {
            // Берём first_name из Telegram (хранится в User)
            String tgName = userRepository.findById(userId)
                    .map(u -> u.getFirstName())
                    .filter(n -> n != null && !n.isBlank())
                    .orElse("Пользователь");
            nameUsed   = tgName;
            nameSource = "telegram";
        }

        // ── Расчёт чисел ──────────────────────────────────────────────────────
        int lifePathNum  = numerologyService.lifePathNumber(profile.getBirthDate());
        int birthdayNum  = numerologyService.birthdayNumber(profile.getBirthDate());
        int soulNum      = numerologyService.portraitSoulNumber(nameUsed);
        int nameNum      = numerologyService.portraitNameNumber(nameUsed);

        // ── Совместимость числа жизни со всеми 9 архетипами (1–9) ─────────────
        List<NumerologyPortraitCompatibilityItem> compatibility = IntStream.rangeClosed(1, 9)
                .mapToObj(n -> new NumerologyPortraitCompatibilityItem(
                        n,
                        contentService.lifePathTitle(n),
                        numerologyService.numberAffinity(lifePathNum, n)
                ))
                .sorted(Comparator.comparingInt(NumerologyPortraitCompatibilityItem::compatibility).reversed())
                .toList();

        // ── Первое открытие портрета — фиксируем в дневнике ─────────────────────
        if (!diaryRepository.existsByUserIdAndFeatureType(userId, DiaryFeatureType.NUMEROLOGY_PORTRAIT)) {
            try {
                String payload = objectMapper.writeValueAsString(Map.of(
                        "event",      "firstView",
                        "nameUsed",   nameUsed,
                        "nameSource", nameSource
                ));
                diaryRepository.save(DiaryEntry.builder()
                        .userId(userId)
                        .featureType(DiaryFeatureType.NUMEROLOGY_PORTRAIT)
                        .payload(payload)
                        .build());
            } catch (Exception e) {
                log.warn("Не удалось сохранить DiaryEntry для портрета userId={}: {}", userId, e.getMessage());
            }
        }

        return new NumerologyPortraitResponse(
                lifePathNum,
                contentService.lifePathTitle(lifePathNum),
                contentService.portraitLifePathDescription(lifePathNum),
                contentService.portraitStrengths(lifePathNum),
                contentService.portraitGrowthPoints(lifePathNum),
                contentService.portraitCalling(lifePathNum),
                contentService.portraitFamousPeople(lifePathNum),

                birthdayNum,
                contentService.lifePathTitle(birthdayNum),
                contentService.portraitShortDescription(birthdayNum),

                soulNum,
                contentService.lifePathTitle(soulNum),
                contentService.portraitShortDescription(soulNum),

                nameNum,
                contentService.lifePathTitle(nameNum),
                contentService.portraitShortDescription(nameNum),

                nameUsed,
                nameSource,

                compatibility
        );
    }
}
