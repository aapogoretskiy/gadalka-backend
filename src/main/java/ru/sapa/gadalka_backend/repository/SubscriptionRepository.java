package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sapa.gadalka_backend.domain.Subscription;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long>, JpaSpecificationExecutor<Subscription> {

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
     * автопродление включено, уведомление ещё не ДОСТАВЛЕНО, до истечения — не больше
     * 3 календарных дней (noticeThreshold = now + 3 дня).
     * <p>
     * Условие именно по renewalNoticeDeliveredAt, а не по «попытке отправки»: если
     * доставить не удалось (пользователь заблокировал бота), подписка намеренно остаётся
     * кандидатом и попытка повторится на следующем цикле — бота могли уже разблокировать.
     * Ценой одного лишнего SELECT в час это спасает автопродление, которое иначе просто
     * не состоялось бы (списывать без доставленного уведомления нельзя).
     * <p>
     * Намеренно НЕ ловим уже истёкшие без уведомления подписки (условие {@code expiresAt > now}) —
     * если предупредить не успели (простой бэкенда), списывать всё равно нельзя: подписка
     * просто истекает как обычно, без автопродления на этот раз (см. SubscriptionRenewalScheduler).
     */
    @Query("""
            SELECT s FROM Subscription s
            WHERE s.status = 'ACTIVE'
              AND s.autoRenewEnabled = true
              AND s.renewalNoticeDeliveredAt IS NULL
              AND s.expiresAt > :now
              AND s.expiresAt <= :noticeThreshold
            """)
    List<Subscription> findAutoRenewNoticeCandidates(@Param("now") OffsetDateTime now,
                                                      @Param("noticeThreshold") OffsetDateTime noticeThreshold);

    /**
     * Кандидаты на реальное автосписание согласно п. 6.12.3 соглашения: списание должно
     * произойти в последние 24 часа ТЕКУЩЕГО расчётного периода — то есть ДО истечения,
     * а не после (условие {@code expiresAt > now AND expiresAt <= chargeWindowEnd}, где
     * chargeWindowEnd = now + 24ч).
     * <p>
     * Обязательное уведомление к этому моменту должно быть не просто отправлено, а
     * ДОСТАВЛЕНО (renewalNoticeDeliveredAt), и с момента доставки должно пройти не меньше
     * 24 часов — noticeDeadline = now - 24ч. Это прямое требование ст. 16.1 ЗоЗПП (376-ФЗ):
     * раньше проверки не было вовсе, и при простое бэкенда уведомление могло уйти и деньги
     * списаться в один и тот же час.
     */
    @Query("""
            SELECT s FROM Subscription s
            WHERE s.status = 'ACTIVE'
              AND s.autoRenewEnabled = true
              AND s.expiresAt > :now
              AND s.expiresAt <= :chargeWindowEnd
              AND s.renewalNoticeDeliveredAt IS NOT NULL
              AND s.renewalNoticeDeliveredAt <= :noticeDeadline
            """)
    List<Subscription> findAutoRenewChargeCandidates(@Param("now") OffsetDateTime now,
                                                      @Param("chargeWindowEnd") OffsetDateTime chargeWindowEnd,
                                                      @Param("noticeDeadline") OffsetDateTime noticeDeadline);

    /**
     * Подписки, зависшие в RENEWAL_PENDING дольше разумного времени ожидания вебхука —
     * скорее всего, платёж уже CANCELLED (см. PendingPaymentExpiryService, таймаут 30 мин
     * + запас на цикл самой этой задачи) или зафейлился без явного сигнала. Кандидат на
     * {@code SubscriptionRenewalScheduler#reconcileStuckRenewals} — считаем это неудачной
     * попыткой и переводим в SUSPENDED (или EXPIRED, если 7 дней ретраев уже вышли).
     */
    @Query("""
            SELECT s FROM Subscription s
            WHERE s.status = 'RENEWAL_PENDING'
              AND s.lastRenewalAttemptAt <= :cutoff
            """)
    List<Subscription> findStuckRenewalPending(@Param("cutoff") OffsetDateTime cutoff);

    /**
     * Подписки в SUSPENDED (списание не удалось хотя бы раз), у которых с последней попытки
     * прошло не меньше суток — можно пробовать снова (п. 6.13.1: не чаще 1 раза в сутки).
     * Проверка «не вышли ли уже 7 дней ретраев» — внутри самого шедулера, не в запросе,
     * т.к. там же нужно решить, ретраить ещё раз или уже завершать подписку.
     */
    @Query("""
            SELECT s FROM Subscription s
            WHERE s.status = 'SUSPENDED'
              AND s.autoRenewEnabled = true
              AND s.lastRenewalAttemptAt <= :retryCutoff
            """)
    List<Subscription> findAutoRenewRetryCandidates(@Param("retryCutoff") OffsetDateTime retryCutoff);

    // ── Счётчики для вкладки «Подписчики» в админке (AdminSubscriptionService#stats) ──

    /** Действующие подписки: статус ACTIVE и срок ещё не истёк */
    @Query("""
            SELECT COUNT(s) FROM Subscription s
            WHERE s.status = 'ACTIVE' AND s.expiresAt > :now
            """)
    long countActive(@Param("now") OffsetDateTime now);

    /** Действующие подписки с включённым автопродлением */
    @Query("""
            SELECT COUNT(s) FROM Subscription s
            WHERE s.status = 'ACTIVE' AND s.expiresAt > :now AND s.autoRenewEnabled = true
            """)
    long countActiveWithAutoRenew(@Param("now") OffsetDateTime now);

    /** Действующие подписки, истекающие в ближайшие N дней (until = now + N дней) */
    @Query("""
            SELECT COUNT(s) FROM Subscription s
            WHERE s.status = 'ACTIVE' AND s.expiresAt > :now AND s.expiresAt <= :until
            """)
    long countExpiringBefore(@Param("now") OffsetDateTime now, @Param("until") OffsetDateTime until);

    long countByStatus(String status);

    /**
     * «Зомби»: автопродление включено, но списать по такой подписке уже невозможно —
     * она вне активных статусов либо ACTIVE с истёкшим сроком, а все выборки шедулера
     * требуют ACTIVE/SUSPENDED/RENEWAL_PENDING. Пользователь при этом видит включённое
     * автопродление, которого фактически нет. В норме должно быть 0.
     */
    @Query("""
            SELECT COUNT(s) FROM Subscription s
            WHERE s.autoRenewEnabled = true
              AND (s.status NOT IN ('ACTIVE', 'SUSPENDED', 'RENEWAL_PENDING')
                   OR (s.status = 'ACTIVE' AND s.expiresAt <= :now))
            """)
    long countAutoRenewZombies(@Param("now") OffsetDateTime now);

    /**
     * Сумма зафиксированных цен по действующим подпискам на автопродлении (в копейках) —
     * сколько спишется за следующий полный цикл, если все продления пройдут успешно.
     */
    @Query("""
            SELECT COALESCE(SUM(s.lockedPriceRub), 0) FROM Subscription s
            WHERE s.status = 'ACTIVE' AND s.expiresAt > :now AND s.autoRenewEnabled = true
            """)
    long sumAutoRenewLockedPrice(@Param("now") OffsetDateTime now);

    /**
     * Активные подписки с включённым автопродлением на конкретном плане — адресаты
     * уведомления об изменении цены плана (п. 6.14.2), см. SubscriptionPlanAdminService.
     */
    @Query("""
            SELECT s FROM Subscription s
            WHERE s.status = 'ACTIVE'
              AND s.autoRenewEnabled = true
              AND s.planId = :planId
            """)
    List<Subscription> findActiveAutoRenewByPlanId(@Param("planId") Long planId);

    /**
     * «Видимая» подписка для блока «Моя подписка» в профиле — в отличие от
     * {@link #findActiveByUserId}, включает и SUSPENDED (приостановленную, но ещё не
     * завершённую автопродлением, см. п. 6.13.2 — пользователь должен видеть её статус,
     * а не «подписки нет»). Для SUSPENDED условие по expiresAt не применяется — на момент
     * приостановки период уже почти или полностью истёк, это ожидаемо.
     */
    @Query("""
            SELECT s FROM Subscription s
            WHERE s.userId = :userId
              AND ((s.status = 'ACTIVE' AND s.expiresAt > :now) OR s.status = 'SUSPENDED')
            ORDER BY s.expiresAt DESC
            LIMIT 1
            """)
    Optional<Subscription> findActiveOrSuspendedByUserId(@Param("userId") Long userId,
                                                          @Param("now") OffsetDateTime now);

    boolean existsByUserIdAndStatus(Long userId, String status);
}
