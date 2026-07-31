package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sapa.gadalka_backend.domain.Subscription;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    @Query("""
            SELECT s FROM Subscription s
            WHERE s.userId = :userId
              AND s.status = 'ACTIVE'
              AND s.expiresAt > :now
            ORDER BY s.expiresAt DESC
            LIMIT 1
            """)
    Optional<Subscription> findActiveByUserId(@Param("userId") Long userId,
                                              @Param("now") OffsetDateTime now);

    /**
     * Активные подписки, истекающие до указанного момента —
     * кандидаты на напоминание (см. SubscriptionReminderScheduler).
     */
    @Query("""
            SELECT s FROM Subscription s
            WHERE s.status = 'ACTIVE'
              AND s.expiresAt > :now
              AND s.expiresAt < :until
            """)
    List<Subscription> findActiveExpiringBefore(@Param("now") OffsetDateTime now,
                                                @Param("until") OffsetDateTime until);

    /**
     * Кандидаты на обязательное по п. 6.12.4 соглашения уведомление об автосписании:
     * автопродление включено, уведомление ещё не отправлено, до истечения — не больше
     * 3 календарных дней (noticeThreshold = now + 3 дня).
     * <p>
     * Намеренно НЕ ловим уже истёкшие без уведомления подписки (условие {@code expiresAt > now}) —
     * если предупредить не успели (простой бэкенда), списывать всё равно нельзя: подписка
     * просто истекает как обычно, без автопродления на этот раз (см. SubscriptionRenewalScheduler).
     */
    @Query("""
            SELECT s FROM Subscription s
            WHERE s.status = 'ACTIVE'
              AND s.autoRenewEnabled = true
              AND s.renewalNoticeSentAt IS NULL
              AND s.expiresAt > :now
              AND s.expiresAt <= :noticeThreshold
            """)
    List<Subscription> findAutoRenewNoticeCandidates(@Param("now") OffsetDateTime now,
                                                      @Param("noticeThreshold") OffsetDateTime noticeThreshold);

    /**
     * Кандидаты на реальное автосписание согласно п. 6.12.3 соглашения: списание должно
     * произойти в последние 24 часа ТЕКУЩЕГО расчётного периода — то есть ДО истечения,
     * а не после (условие {@code expiresAt > now AND expiresAt <= chargeWindowEnd}, где
     * chargeWindowEnd = now + 24ч). Обязательное уведомление (п. 6.12.4) уже должно быть
     * отправлено к этому моменту.
     */
    @Query("""
            SELECT s FROM Subscription s
            WHERE s.status = 'ACTIVE'
              AND s.autoRenewEnabled = true
              AND s.expiresAt > :now
              AND s.expiresAt <= :chargeWindowEnd
              AND s.renewalNoticeSentAt IS NOT NULL
            """)
    List<Subscription> findAutoRenewChargeCandidates(@Param("now") OffsetDateTime now,
                                                      @Param("chargeWindowEnd") OffsetDateTime chargeWindowEnd);
}
