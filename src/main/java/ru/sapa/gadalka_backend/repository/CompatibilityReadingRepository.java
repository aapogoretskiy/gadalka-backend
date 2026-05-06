package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sapa.gadalka_backend.domain.CompatibilityReading;

import java.util.Optional;

public interface CompatibilityReadingRepository extends JpaRepository<CompatibilityReading, Long> {
    Optional<CompatibilityReading> findByUserIdAndPersonsHash(Long userId, String personsHash);
    Optional<CompatibilityReading> findByIdAndUserId(Long id, Long userId);
}
