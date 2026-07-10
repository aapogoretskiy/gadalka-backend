package ru.sapa.gadalka_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.sapa.gadalka_backend.domain.SensitiveQueryLog;
import ru.sapa.gadalka_backend.domain.type.SensitiveContentCategory;

import java.util.List;

public interface SensitiveQueryLogRepository extends JpaRepository<SensitiveQueryLog, Long> {

    /** Все записи, новые сверху — для вкладки без фильтра */
    Page<SensitiveQueryLog> findAllByOrderByDetectedAtDesc(Pageable pageable);

    /** Фильтрация по категории */
    Page<SensitiveQueryLog> findByCategoryOrderByDetectedAtDesc(SensitiveContentCategory category, Pageable pageable);

    /** Drill-down по конкретному пользователю — для карточки рейтинга в админке */
    Page<SensitiveQueryLog> findByUserIdOrderByDetectedAtDesc(Long userId, Pageable pageable);

    /** Для пересчёта профиля рейтинга целиком по пользователю */
    List<SensitiveQueryLog> findByUserId(Long userId);

    /** Дедуп при бэкафилле: не логировать повторно уже пойманный вопрос */
    boolean existsByUserIdAndQuestion(Long userId, String question);

    /** Для полного пересчёта рейтинга после бэкафилла — все пользователи, у кого есть хоть одна запись */
    @Query("SELECT DISTINCT s.userId FROM SensitiveQueryLog s")
    List<Long> findDistinctUserIds();
}
