package ru.sapa.gadalka_backend.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sapa.gadalka_backend.domain.DailyHoroscope;
import ru.sapa.gadalka_backend.domain.type.ZodiacSign;

import java.util.Optional;

public interface DailyHoroscopeRepository extends JpaRepository<DailyHoroscope, Long> {

    Optional<DailyHoroscope> findByZodiacSign(ZodiacSign zodiacSign);

    /**
     * Pessimistic write lock — используется при регенерации гороскопа на новый день.
     * Блокирует строку знака на время транзакции, чтобы при одновременных запросах
     * нескольких пользователей с одним знаком AI был вызван только один раз
     * (см. HoroscopeService — double-checked locking: без лока читаем кэш,
     * с локом — только если контент за сегодня ещё не сгенерирован).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM DailyHoroscope h WHERE h.zodiacSign = :zodiacSign")
    Optional<DailyHoroscope> findByZodiacSignForUpdate(@Param("zodiacSign") ZodiacSign zodiacSign);
}
