package ru.sapa.gadalka_backend.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sapa.gadalka_backend.api.dto.profile.CreateProfileRequest;
import ru.sapa.gadalka_backend.api.dto.profile.ProfileResponse;
import ru.sapa.gadalka_backend.api.dto.profile.UpdateProfileRequest;
import ru.sapa.gadalka_backend.service.UserProfileService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user-profiles")
@Tag(name = "Профиль пользователя", description = "Контроллер для управления профилем пользователя (CRUD)")
public class UserProfileController extends BaseController {

    private final UserProfileService userProfileService;

    @PostMapping
    @Operation(summary = "Создание профиля пользователя")
    public ProfileResponse create(HttpServletRequest request,
                                  @RequestBody CreateProfileRequest createProfileRequest) {
        return userProfileService.createProfile(resolveUserId(request), createProfileRequest);
    }

    @GetMapping
    @Operation(summary = "Получение профиля пользователя")
    public ProfileResponse get(HttpServletRequest request) {
        return userProfileService.getProfile(resolveUserId(request));
    }

    @PutMapping
    @Operation(summary = "Обновление профиля пользователя")
    public ProfileResponse update(HttpServletRequest request,
                                  @RequestBody UpdateProfileRequest updateRequest) {
        return userProfileService.updateProfile(resolveUserId(request), updateRequest);
    }

    @DeleteMapping
    @Operation(summary = "Удаление профиля пользователя")
    public void delete(HttpServletRequest request) {
        userProfileService.deleteProfile(resolveUserId(request));
    }

    private Long resolveUserId(HttpServletRequest request) {
        return resolveUser(request).getId();
    }

}
