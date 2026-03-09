package ru.sapa.gadalka_backend.api.dto.profile;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

public record UpdateProfileRequest(
        LocalDate birthDate,

        @Schema(type = "string", example = "12:30:00")
        @JsonFormat(pattern = "HH:mm:ss")
        LocalTime birthTime,

        String birthCity,
        Set<Goal> goals
) {
}
