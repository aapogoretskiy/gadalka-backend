package ru.sapa.gadalka_backend.api.dto.admin;

import ru.sapa.gadalka_backend.domain.SensitiveQueryLog;
import ru.sapa.gadalka_backend.domain.User;

import java.time.OffsetDateTime;

public record SensitiveQueryLogDto(
        Long id,
        Long userId,
        String username,
        String firstName,
        String question,
        String category,
        OffsetDateTime detectedAt
) {
    public static SensitiveQueryLogDto from(SensitiveQueryLog log, User user) {
        return new SensitiveQueryLogDto(
                log.getId(),
                log.getUserId(),
                user != null ? user.getUsername() : null,
                user != null ? user.getFirstName() : null,
                log.getQuestion(),
                log.getCategory().name(),
                log.getDetectedAt()
        );
    }
}
