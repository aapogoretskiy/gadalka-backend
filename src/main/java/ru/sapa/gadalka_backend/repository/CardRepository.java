package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.sapa.gadalka_backend.domain.Card;

public interface CardRepository extends JpaRepository<Card, Long> {

    @Query(value = "SELECT * FROM cards ORDER BY random() LIMIT 1", nativeQuery = true)
    Card findRandomCard();
}
