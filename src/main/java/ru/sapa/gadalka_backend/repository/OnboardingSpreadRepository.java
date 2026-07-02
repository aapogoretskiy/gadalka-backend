package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.sapa.gadalka_backend.domain.OnboardingSpread;

import java.util.List;
import java.util.Optional;

public interface OnboardingSpreadRepository extends JpaRepository<OnboardingSpread, Long> {

    /** Список вопросов для кнопок онбординга (в порядке появления в пуле) */
    @Query(value = "SELECT DISTINCT ON (question) question FROM onboarding_spreads " +
            "WHERE is_active ORDER BY question, id", nativeQuery = true)
    List<String> findActiveQuestions();

    /** Случайный активный вариант расклада для выбранного вопроса */
    @Query(value = "SELECT * FROM onboarding_spreads WHERE question = :question AND is_active " +
            "ORDER BY random() LIMIT 1", nativeQuery = true)
    Optional<OnboardingSpread> findRandomByQuestion(String question);
}
