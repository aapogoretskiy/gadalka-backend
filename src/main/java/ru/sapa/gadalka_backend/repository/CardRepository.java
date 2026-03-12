package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sapa.gadalka_backend.domain.Card;

import java.util.List;

public interface CardRepository extends JpaRepository<Card, Long> {

    @Query(value = "SELECT * FROM cards ORDER BY random() LIMIT 1", nativeQuery = true)
    Card findRandomCard();

    @Query(value = "SELECT * FROM cards ORDER BY random() LIMIT :limit", nativeQuery = true)
    List<Card> findRandomCards(@Param("limit") int limit);
}
