package ru.sapa.gadalka_backend.api.dto.diary;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class DiaryHistoryResponse {
    private List<DiaryEntryDto> entries;
}
