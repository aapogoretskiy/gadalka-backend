package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.sapa.gadalka_backend.domain.ReferralEvent;
import ru.sapa.gadalka_backend.domain.type.ReferralEventType;

import java.util.List;
import java.util.Optional;

public interface ReferralEventRepository extends JpaRepository<ReferralEvent, Long> {

    /** Найти последнее BOT_ENTRY событие для данного telegram_id (для связки с APP_OPEN). */
    Optional<ReferralEvent> findTopByTelegramIdAndEventTypeOrderByCreatedAtDesc(
            Long telegramId, ReferralEventType eventType);

    /** Все события по реферальному коду (для отладки). */
    List<ReferralEvent> findAllByReferralCodeOrderByCreatedAtDesc(String referralCode);
}
