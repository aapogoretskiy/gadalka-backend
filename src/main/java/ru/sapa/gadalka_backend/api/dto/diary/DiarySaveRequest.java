package ru.sapa.gadalka_backend.api.dto.diary;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;

@Getter
@NoArgsConstructor
public class DiarySaveRequest {

    @NotNull
    private DiaryFeatureType featureType;

    @NotNull
    private Long referenceId;
}
