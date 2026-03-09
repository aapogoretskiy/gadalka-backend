package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.sapa.gadalka_backend.domain.UserProfile;

import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    @Query("""
       SELECT up
       FROM UserProfile up
       LEFT JOIN FETCH up.goals
       WHERE up.user.id = :userId
       """)
    Optional<UserProfile> findByUserId(Long userId);
}
