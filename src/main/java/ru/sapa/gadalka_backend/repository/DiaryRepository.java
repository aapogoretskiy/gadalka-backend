package ru.sapa.gadalka_backend.repository;

import org.springframework.data.domain.Pageable;
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

    /**
     * Используется админ-панелью для построения истории действий пользователя.
     * Гороскоп на день хранится не в отдельной таблице на пользователя (контент общий
     * на знак зодиака), а только как запись дневника — поэтому для админки это
     * единственный источник истории по этому типу.
     */
    List<DiaryEntry> findByUserIdAndFeatureTypeOrderByCreatedAtDesc(
            Long userId,
            DiaryFeatureType featureType,
            Pageable pageable
    );

    /**
     * Используется гороскопом на день: контент общий на знак зодиака (а не на пользователя),
     * поэтому перед записью в дневник нужно отдельно проверять, не сохраняли ли мы
     * уже запись этому пользователю сегодня — иначе при каждом открытии экрана
     * плодились бы дубликаты в истории.
     */
    boolean existsByUserIdAndFeatureTypeAndCreatedAtBetween(
            Long userId,
            DiaryFeatureType featureType,
            OffsetDateTime from,
            OffsetDateTime to
    );

    /**
     * Проверяет, существует ли хотя бы одна запись данного типа для пользователя.
     * Используется для определения первого открытия нумерологического портрета.
     */
    boolean existsByUserIdAndFeatureType(Long userId, DiaryFeatureType featureType);
}
