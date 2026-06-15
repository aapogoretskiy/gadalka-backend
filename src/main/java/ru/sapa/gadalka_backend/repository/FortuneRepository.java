package ru.sapa.gadalka_backend.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.sapa.gadalka_backend.domain.Fortune;
import ru.sapa.gadalka_backend.domain.type.SpreadType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface FortuneRepository extends JpaRepository<Fortune, Long> {
    Optional<Fortune> findByUserIdAndQuestionHash(Long userId, String questionHash);

    /** Проверка владельца — используется FortuneFeedbackValidator */
    boolean existsByIdAndUserId(Long id, Long userId);

    // ── История действий пользователя (для админки) ──────────────────────────

    /** Последние гадания пользователя (для lazy-панели в AdminController) */
    List<Fortune> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // ── Отчёты ──────────────────────────────────────────────────────────────

    /** Количество гаданий после указанной даты */
    long countByCreatedAtAfter(OffsetDateTime from);

    /** Количество гаданий конкретного типа после указанной даты */
    long countByCreatedAtAfterAndSpreadType(OffsetDateTime from, SpreadType spreadType);

    /**
     * Количество гаданий без явного spreadType после указанной даты.
     * Старые записи (до введения SpreadType) считаются THREE_CARD.
     */
    long countByCreatedAtAfterAndSpreadTypeIsNull(OffsetDateTime from);

    // ── Отчёты за диапазон ──────────────────────────────────────────────────

    /** Количество гаданий за диапазон дат */
    long countByCreatedAtBetween(OffsetDateTime from, OffsetDateTime to);

    /** Количество гаданий конкретного типа за диапазон дат */
    long countByCreatedAtBetweenAndSpreadType(OffsetDateTime from, OffsetDateTime to, SpreadType spreadType);

    /** Количество гаданий без spreadType за диапазон дат (считаются THREE_CARD) */
    long countByCreatedAtBetweenAndSpreadTypeIsNull(OffsetDateTime from, OffsetDateTime to);
}
