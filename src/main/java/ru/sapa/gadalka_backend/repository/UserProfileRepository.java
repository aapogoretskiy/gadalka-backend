package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.sapa.gadalka_backend.domain.UserProfile;
import ru.sapa.gadalka_backend.domain.type.NotificationTime;

import java.util.List;
import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    @Query("""
       SELECT up
       FROM UserProfile up
       LEFT JOIN FETCH up.goals
       WHERE up.user.id = :userId
       """)
    Optional<UserProfile> findByUserId(Long userId);

    /**
     * Выбирает все профили с заданным временем уведомлений.
     * Используется планировщиком: утром — MORNING, вечером — EVENING.
     */
    @Query("""
       SELECT up
       FROM UserProfile up
       JOIN FETCH up.user
       WHERE up.notificationTime = :notificationTime
       """)
    List<UserProfile> findByNotificationTime(NotificationTime notificationTime);
}
