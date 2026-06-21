package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sapa.gadalka_backend.domain.QuestionPreset;

import java.util.List;

public interface QuestionPresetRepository extends JpaRepository<QuestionPreset, Long> {

    List<QuestionPreset> findAllByIsActiveTrueOrderBySortOrderAsc();
}
