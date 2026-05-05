package ru.sapa.gadalka_backend.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sapa.gadalka_backend.domain.UserFortuneCredit;

import java.util.Optional;

public interface UserFortuneCreditRepository extends JpaRepository<UserFortuneCredit, Long> {

    Optional<UserFortuneCredit> findByUserId(Long userId);

    /**
     * Pessimistic write lock — используется при списании кредита.
     * Блокирует строку на время транзакции, защищая от гонки
     * когда два запроса одновременно пытаются потратить последний кредит.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserFortuneCredit u WHERE u.userId = :userId")
    Optional<UserFortuneCredit> findByUserIdForUpdate(@Param("userId") Long userId);
}
