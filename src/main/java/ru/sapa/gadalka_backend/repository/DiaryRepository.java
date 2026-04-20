package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sapa.gadalka_backend.domain.DiaryEntry;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;

import java.time.OffsetDateTime;
import java.util.List;

public interface DiaryRepository extends JpaRepository<DiaryEntry, Long> {

    List<DiaryEntry> findByUserIdAndFeatureTypeAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long userId,
            DiaryFeatureType featureType,
            OffsetDateTime from,
            OffsetDateTime to
    );
}
