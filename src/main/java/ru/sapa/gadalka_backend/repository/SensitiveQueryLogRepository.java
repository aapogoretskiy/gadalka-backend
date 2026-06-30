package ru.sapa.gadalka_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.sapa.gadalka_backend.domain.SensitiveQueryLog;
import ru.sapa.gadalka_backend.domain.type.SensitiveContentCategory;

public interface SensitiveQueryLogRepository extends JpaRepository<SensitiveQueryLog, Long> {

    /** Все записи, новые сверху — для вкладки без фильтра */
    Page<SensitiveQueryLog> findAllByOrderByDetectedAtDesc(Pageable pageable);

    /** Фильтрация по категории */
    Page<SensitiveQueryLog> findByCategoryOrderByDetectedAtDesc(SensitiveContentCategory category, Pageable pageable);
}
