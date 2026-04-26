package ru.sapa.gadalka_backend.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.api.dto.profile.CreateProfileRequest;
import ru.sapa.gadalka_backend.api.dto.profile.ProfileResponse;
import ru.sapa.gadalka_backend.api.dto.profile.UpdateProfileRequest;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.UserProfile;
import ru.sapa.gadalka_backend.repository.UserProfileRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

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
        UserProfile userProfile = UserProfile.builder()
                .user(user)
                .birthDate(createRequest.birthDate())
                .birthTime(createRequest.birthTime())
                .birthCity(createRequest.birthCity())
                .goals(createRequest.goals())
                .build();

        userProfileRepository.save(userProfile);

        return map(userProfile);
    }

    @Transactional
    public ProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        Optional<UserProfile> userProfileOpt = userProfileRepository.findByUserId(userId);
        if (userProfileOpt.isEmpty()) {
            throw new RuntimeException(String.format("Cannot find user profile by user id: %s", userId));
        }
        UserProfile userProfile = userProfileOpt.get();

        userProfile.setBirthDate(request.birthDate());
        userProfile.setBirthTime(request.birthTime());
        userProfile.setBirthCity(request.birthCity());
        userProfile.setGoals(request.goals());

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
                profile.getGoals()
        );
    }
}
