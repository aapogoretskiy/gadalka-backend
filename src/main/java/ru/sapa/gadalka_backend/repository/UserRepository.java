package ru.sapa.gadalka_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.sapa.gadalka_backend.domain.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByTelegramId(Long telegramId);

    /** Поиск по username (без учёта регистра, частичное совпадение) для админки */
    Page<User> findByUsernameContainingIgnoreCase(String username, Pageable pageable);
}
