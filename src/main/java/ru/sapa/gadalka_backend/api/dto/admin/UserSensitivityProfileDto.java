package ru.sapa.gadalka_backend.api.dto.admin;

import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.UserSensitivityProfile;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Карточка рейтинга "склонности к чувствительным вопросам" для вкладки админки. */
public record UserSensitivityProfileDto(
        Long userId,
        String username,
        String firstName,
        int totalTextQuestions,
        int totalSensitiveCount,
        BigDecimal sensitivePercentage,
        String dominantCategory,
        String riskLevel,
        String categoryCountsJson,
        OffsetDateTime updatedAt
) {
    public static UserSensitivityProfileDto from(UserSensitivityProfile profile, User user) {
        return new UserSensitivityProfileDto(
                profile.getUserId(),
                user != null ? user.getUsername() : null,
                user != null ? user.getFirstName() : null,
                profile.getTotalTextQuestions(),
                profile.getTotalSensitiveCount(),
                profile.getSensitivePercentage(),
                profile.getDominantCategory() != null ? profile.getDominantCategory().name() : null,
                profile.getRiskLevel().name(),
                profile.getCategoryCounts(),
                profile.getUpdatedAt()
        );
    }
}
