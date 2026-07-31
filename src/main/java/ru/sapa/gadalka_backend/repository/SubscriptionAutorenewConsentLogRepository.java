package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sapa.gadalka_backend.domain.SubscriptionAutorenewConsentLog;

import java.util.List;

public interface SubscriptionAutorenewConsentLogRepository extends JpaRepository<SubscriptionAutorenewConsentLog, Long> {

    /** История согласий конкретного пользователя — для админки и разбора спорных списаний */
    List<SubscriptionAutorenewConsentLog> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
