package ru.sapa.gadalka_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.api.dto.profile.CreateProfileRequest;
import ru.sapa.gadalka_backend.api.dto.profile.ProfileResponse;
import ru.sapa.gadalka_backend.api.dto.profile.UpdateProfileRequest;
import ru.sapa.gadalka_backend.domain.DiaryEntry;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.UserProfile;
import ru.sapa.gadalka_backend.domain.type.CreditTransactionReason;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;
import ru.sapa.gadalka_backend.domain.type.NotificationTime;
import ru.sapa.gadalka_backend.repository.DiaryRepository;
import ru.sapa.gadalka_backend.repository.UserProfileRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final DiaryRepository diaryRepository;
    private final ObjectMapper objectMapper;
    private final FortuneCreditService fortuneCreditService;

    private static final int PROFILE_REWARD_CREDITS = 1;

    @Transactional
    public ProfileResponse getProfile(Long userId) {
        return map(userProfileRepository.findByUserId(userId)
                .orElseThrow());
    }

    @Transactional
    public ProfileResponse createProfile(Long userId, CreateProfileRequest createRequest) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new RuntimeException(String.format("Cannot find user by id: %s", userId));
        }
        User user = userOpt.get();

        NotificationTime notificationTime = createRequest.notificationTime() != null
                ? createRequest.notificationTime()
                : NotificationTime.EVENING;

        UserProfile userProfile = UserProfile.builder()
                .user(user)
                .birthDate(createRequest.birthDate())
                .birthTime(createRequest.birthTime())
                .birthCity(createRequest.birthCity())
                .goals(createRequest.goals())
                .notificationTime(notificationTime)
                .build();

        userProfileRepository.save(userProfile);

        if (user.getTermsAcceptedAt() == null) {
            user.setTermsAcceptedAt(OffsetDateTime.now());
            user.setTermsVersion(createRequest.termsVersion());
        }
        userRepository.save(user);

        fortuneCreditService.grantCredits(userId, PROFILE_REWARD_CREDITS, CreditTransactionReason.PROFILE_REWARD, null);
        log.info("Начислен бонус за заполнение профиля: userId={}, credits={}", userId, PROFILE_REWARD_CREDITS);

        return map(userProfile);
    }

    @Transactional
    public ProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        Optional<UserProfile> userProfileOpt = userProfileRepository.findByUserId(userId);
        if (userProfileOpt.isEmpty()) {
            throw new RuntimeException(String.format("Cannot find user profile by user id: %s", userId));
        }
        UserProfile userProfile = userProfileOpt.get();

        if (request.birthDate() != null) {
            userProfile.setBirthDate(request.birthDate());
        }
        if (request.birthTime() != null) {
            userProfile.setBirthTime(request.birthTime());
        }
        if (request.birthCity() != null) {
            userProfile.setBirthCity(request.birthCity());
        }
        if (request.goals() != null) {
            userProfile.setGoals(request.goals());
        }
        if (request.notificationTime() != null) {
            userProfile.setNotificationTime(request.notificationTime());
        }
        if (request.numerologyName() != null) {
            String oldName = userProfile.getNumerologyName();
            String newName = request.numerologyName().isBlank() ? null : request.numerologyName().trim();

            // Фиксируем в истории любое изменение имени (null→имя, имя→другое имя)
            if (!Objects.equals(oldName, newName) && newName != null) {
                try {
                    String payload = objectMapper.writeValueAsString(Map.of(
                            "event",  "nameChanged",
                            "from",   oldName != null ? oldName : "",
                            "to",     newName
                    ));
                    diaryRepository.save(DiaryEntry.builder()
                            .userId(userId)
                            .featureType(DiaryFeatureType.NUMEROLOGY_PORTRAIT)
                            .payload(payload)
                            .build());
                } catch (Exception e) {
                    log.warn("Не удалось сохранить DiaryEntry смены имени userId={}: {}", userId, e.getMessage());
                }
            }

            userProfile.setNumerologyName(newName);
        }

        userProfileRepository.save(userProfile);
        return map(userProfile);
    }

    @Transactional
    public void deleteProfile(Long userId) {
        Optional<UserProfile> userProfileOpt = userProfileRepository.findByUserId(userId);
        if (userProfileOpt.isEmpty()) {
            throw new RuntimeException(String.format("Cannot find user profile by user id: %s", userId));
        }
        userProfileRepository.delete(userProfileOpt.get());
    }

    private ProfileResponse map(UserProfile profile) {
        return new ProfileResponse(
                profile.getId(),
                profile.getBirthDate(),
                profile.getBirthTime(),
                profile.getBirthCity(),
                profile.getGoals(),
                profile.getNotificationTime()
        );
    }
}
