package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sapa.gadalka_backend.domain.DreamSymbol;

import java.util.List;

public interface DreamSymbolRepository extends JpaRepository<DreamSymbol, Long> {

    /** Активные символы для чипов на экране Сонника, в порядке sort_order. */
    List<DreamSymbol> findByIsActiveTrueOrderBySortOrderAsc();

    /** Все символы для админ-панели (включая выключенные). */
    List<DreamSymbol> findAllByOrderBySortOrderAsc();
}
