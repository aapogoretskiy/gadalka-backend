package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sapa.gadalka_backend.domain.FortuneCreditLogEntry;

import java.util.List;

public interface FortuneCreditLogRepository extends JpaRepository<FortuneCreditLogEntry, Long> {

    List<FortuneCreditLogEntry> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
