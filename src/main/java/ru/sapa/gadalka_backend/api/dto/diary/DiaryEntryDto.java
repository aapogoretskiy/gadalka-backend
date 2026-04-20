package ru.sapa.gadalka_backend.api.dto.diary;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;

import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor
public class DiaryEntryDto {
    private Long id;
    private DiaryFeatureType featureType;
    private OffsetDateTime createdAt;
    /** Данные результата — структура зависит от featureType */
    private JsonNode data;
}
