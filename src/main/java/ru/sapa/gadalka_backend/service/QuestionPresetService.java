package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.api.dto.preset.QuestionCategoryDto;
import ru.sapa.gadalka_backend.api.dto.preset.QuestionPresetDto;
import ru.sapa.gadalka_backend.domain.QuestionCategory;
import ru.sapa.gadalka_backend.domain.QuestionPreset;
import ru.sapa.gadalka_backend.repository.QuestionCategoryRepository;
import ru.sapa.gadalka_backend.repository.QuestionPresetRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionPresetService {

    private final QuestionCategoryRepository categoryRepository;
    private final QuestionPresetRepository presetRepository;

    /**
     * Возвращает все активные категории вопросов с вложенными активными пресетами,
     * отсортированными по sort_order. Используется на экране "О чём спросить карты?" —
     * данные почти статичны и редактируются только через миграции, поэтому без кэша.
     */
    @Transactional(readOnly = true)
    public List<QuestionCategoryDto> getCategoriesWithPresets() {
        List<QuestionCategory> categories = categoryRepository.findAllByIsActiveTrueOrderBySortOrderAsc();
        List<QuestionPreset> presets = presetRepository.findAllByIsActiveTrueOrderBySortOrderAsc();

        Map<Long, List<QuestionPresetDto>> presetsByCategoryId = presets.stream()
                .collect(Collectors.groupingBy(
                        preset -> preset.getCategory().getId(),
                        Collectors.mapping(QuestionPresetDto::from, Collectors.toList())
                ));

        return categories.stream()
                .map(category -> QuestionCategoryDto.from(
                        category,
                        presetsByCategoryId.getOrDefault(category.getId(), List.of())
                ))
                .toList();
    }
}
