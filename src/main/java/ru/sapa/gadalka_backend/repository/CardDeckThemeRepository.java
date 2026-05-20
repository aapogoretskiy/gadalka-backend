package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sapa.gadalka_backend.domain.CardDeckTheme;

import java.util.List;
import java.util.Optional;

public interface CardDeckThemeRepository extends JpaRepository<CardDeckTheme, Long> {

    Optional<CardDeckTheme> findBySlug(String slug);

    List<CardDeckTheme> findAllByOrderBySortOrderAsc();
}
