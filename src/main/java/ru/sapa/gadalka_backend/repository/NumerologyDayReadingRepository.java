package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sapa.gadalka_backend.domain.NumerologyDayReading;

import java.time.LocalDate;
import java.util.Optional;

public interface NumerologyDayReadingRepository extends JpaRepository<NumerologyDayReading, Long> {

    Optional<NumerologyDayReading> findByUserIdAndDate(Long userId, LocalDate date);

    Optional<NumerologyDayReading> findByIdAndUserId(Long id, Long userId);
}
