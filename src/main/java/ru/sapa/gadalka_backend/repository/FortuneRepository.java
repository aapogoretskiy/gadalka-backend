package ru.sapa.gadalka_backend.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sapa.gadalka_backend.domain.Fortune;
import ru.sapa.gadalka_backend.domain.type.SpreadType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface FortuneRepository extends JpaRepository<Fortune, Long> {
    Optional<Fortune> findByUserIdAndQuestionHash(Long userId, String questionHash);

    boolean existsByIdAndUserId(Long id, Long userId);

    boolean existsByUserId(Long userId);

    /** Знаменатель для рейтинга "склонности к чувствительным вопросам" — см. UserSensitivityProfileService. */
    long countByUserId(Long userId);

    // ── История действий пользователя ────────────────────────────────────────

    List<Fortune> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // ── Отчёты — стандартные методы ──────────────────────────────────────────

    long countByCreatedAtAfter(OffsetDateTime from);
    long countByCreatedAtAfterAndSpreadType(OffsetDateTime from, SpreadType spreadType);
    long countByCreatedAtAfterAndSpreadTypeIsNull(OffsetDateTime from);
    long countByCreatedAtBetween(OffsetDateTime from, OffsetDateTime to);
    long countByCreatedAtBetweenAndSpreadType(OffsetDateTime from, OffsetDateTime to, SpreadType spreadType);
    long countByCreatedAtBetweenAndSpreadTypeIsNull(OffsetDateTime from, OffsetDateTime to);

    // ── Отчёты — source-aware методы (JOIN с users) ───────────────────────────
    // Токен "__organic__" = referral_source IS NULL (органические пользователи).
    // source = null → фильтр отключён.

    @Query(value = "SELECT COUNT(f.*) FROM fortunes f " +
            "JOIN users u ON u.id = f.user_id " +
            "WHERE (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    long countWithSourceFilter(@Param("source") String source);

    @Query(value = "SELECT COUNT(f.*) FROM fortunes f " +
            "JOIN users u ON u.id = f.user_id " +
            "WHERE f.created_at > :from " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    long countByCreatedAtAfterWithSource(@Param("from") OffsetDateTime from, @Param("source") String source);

    @Query(value = "SELECT COUNT(f.*) FROM fortunes f " +
            "JOIN users u ON u.id = f.user_id " +
            "WHERE f.created_at > :from AND f.spread_type = :spreadType " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    long countByCreatedAtAfterAndSpreadTypeWithSource(
            @Param("from") OffsetDateTime from, @Param("spreadType") String spreadType, @Param("source") String source);

    @Query(value = "SELECT COUNT(f.*) FROM fortunes f " +
            "JOIN users u ON u.id = f.user_id " +
            "WHERE f.created_at > :from AND f.spread_type IS NULL " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    long countByCreatedAtAfterAndSpreadTypeIsNullWithSource(
            @Param("from") OffsetDateTime from, @Param("source") String source);

    @Query(value = "SELECT COUNT(f.*) FROM fortunes f " +
            "JOIN users u ON u.id = f.user_id " +
            "WHERE f.created_at >= :from AND f.created_at <= :to " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    long countByCreatedAtBetweenWithSource(
            @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to, @Param("source") String source);

    @Query(value = "SELECT COUNT(f.*) FROM fortunes f " +
            "JOIN users u ON u.id = f.user_id " +
            "WHERE f.created_at >= :from AND f.created_at <= :to AND f.spread_type = :spreadType " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    long countByCreatedAtBetweenAndSpreadTypeWithSource(
            @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to,
            @Param("spreadType") String spreadType, @Param("source") String source);

    @Query(value = "SELECT COUNT(f.*) FROM fortunes f " +
            "JOIN users u ON u.id = f.user_id " +
            "WHERE f.created_at >= :from AND f.created_at <= :to AND f.spread_type IS NULL " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    long countByCreatedAtBetweenAndSpreadTypeIsNullWithSource(
            @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to, @Param("source") String source);
}
