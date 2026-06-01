package ru.sapa.gadalka_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.sapa.gadalka_backend.domain.User;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByTelegramId(Long telegramId);

    /** Поиск по username (без учёта регистра, частичное совпадение) для админки */
    Page<User> findByUsernameContainingIgnoreCase(String username, Pageable pageable);

    // ── Отчёты ──────────────────────────────────────────────────────────────

    /** Количество новых пользователей после указанной даты */
    long countByCreatedAtAfter(OffsetDateTime from);

    /** Количество активных пользователей после указанной даты (по lastActiveAt) */
    long countByLastActiveAtAfter(OffsetDateTime from);
}
