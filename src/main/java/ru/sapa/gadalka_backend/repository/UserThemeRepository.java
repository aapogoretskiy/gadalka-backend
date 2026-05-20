package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sapa.gadalka_backend.domain.UserTheme;
import ru.sapa.gadalka_backend.domain.UserThemeId;

import java.util.Set;

public interface UserThemeRepository extends JpaRepository<UserTheme, UserThemeId> {

    /**
     * Возвращает набор ID тем, купленных пользователем.
     * Используем Set<Long> — быстрый поиск по contains() при формировании списка тем.
     */
    @Query("SELECT ut.id.themeId FROM UserTheme ut WHERE ut.id.userId = :userId")
    Set<Long> findThemeIdsByUserId(@Param("userId") Long userId);

    /** Проверить, владеет ли пользователь конкретной темой */
    boolean existsById(UserThemeId id);
}
