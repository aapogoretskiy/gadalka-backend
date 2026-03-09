package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.sapa.gadalka_backend.domain.DailyCard;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyCardRepository extends JpaRepository<DailyCard, Long> {

    @Query("""
                SELECT dc
                FROM DailyCard dc
                JOIN FETCH dc.card
                WHERE dc.userId = :userId
                AND dc.date = :date
            """)
    Optional<DailyCard> findByUserIdAndDate(Long userId, LocalDate date);
}
