package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sapa.gadalka_backend.domain.Fortune;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface FortuneRepository extends JpaRepository<Fortune, Long> {
    Optional<Fortune> findByUserIdAndQuestionHash(Long userId, String questionHash);

    // ── Отчёты ──────────────────────────────────────────────────────────────

    /** Количество гаданий после указанной даты */
    long countByCreatedAtAfter(OffsetDateTime from);
}
