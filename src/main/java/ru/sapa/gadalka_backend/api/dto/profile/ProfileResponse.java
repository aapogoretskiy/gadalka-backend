package ru.sapa.gadalka_backend.api.dto.profile;

import ru.sapa.gadalka_backend.domain.type.NotificationTime;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

public record ProfileResponse(
        Long id,
        LocalDate birthDate,
        LocalTime birthTime,
        String birthCity,
        Set<Goal> goals,
        NotificationTime notificationTime
) {}
