package ru.sapa.gadalka_backend.api.dto.admin.subscription;

import ru.sapa.gadalka_backend.domain.Subscription;
import ru.sapa.gadalka_backend.domain.User;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Строка вкладки «Подписчики» в админ-панели: одна купленная подписка,
 * обогащённая отображаемыми данными пользователя.
 *
 * @param daysLeft   дней до истечения; отрицательное — подписка уже истекла, но статус
 *                   этого ещё не отражает (см. {@code zombie})
 * @param retryDeadline до какого момента идут повторные попытки списания (первая неудача + 7 дней,
 *                      п. 6.13.1 соглашения). NULL — неудачных попыток в этом цикле не было
 * @param noticeDelivered доставлено ли обязательное уведомление об автосписании. Если попытка была,
 *                        а доставки нет — списания не будет, и это видно прямо в списке
 * @param zombie     автопродление формально включено, но фактически уже невозможно: подписка
 *                   вне активных статусов либо ACTIVE с истёкшим сроком. Шедулер такие не берёт,
 *                   пользователь при этом видит включённое автопродление. Строк с zombie = true
 *                   в норме быть не должно — каждая означает недоработанный сценарий закрытия
 */
public record AdminSubscriptionRowDto(
        Long id,
        Long userId,
        Long telegramId,
        String username,
        String firstName,
        Long planId,
        String planName,
        String status,
        String provider,
        OffsetDateTime startedAt,
        OffsetDateTime expiresAt,
        long daysLeft,
        boolean autoRenewEnabled,
        Integer lockedPriceMinor,
        Long rootPaymentId,
        OffsetDateTime renewalNoticeSentAt,
        OffsetDateTime renewalNoticeDeliveredAt,
        boolean noticeDelivered,
        OffsetDateTime lastRenewalAttemptAt,
        OffsetDateTime renewalFirstFailedAt,
        OffsetDateTime retryDeadline,
        boolean zombie,
        OffsetDateTime cancelledAt,
        OffsetDateTime createdAt
) {

    /** Сколько дней подряд повторяются неудачные списания, прежде чем подписка завершается (п. 6.13.1) */
    private static final int MAX_RETRY_DAYS = 7;

    public static AdminSubscriptionRowDto from(Subscription s, User user, OffsetDateTime now) {
        boolean autoRenew = Boolean.TRUE.equals(s.getAutoRenewEnabled());
        boolean chargeable = "ACTIVE".equals(s.getStatus()) && s.getExpiresAt().isAfter(now)
                || "SUSPENDED".equals(s.getStatus())
                || "RENEWAL_PENDING".equals(s.getStatus());

        return new AdminSubscriptionRowDto(
                s.getId(),
                s.getUserId(),
                user != null ? user.getTelegramId() : null,
                user != null ? user.getUsername() : null,
                user != null ? user.getFirstName() : null,
                s.getPlanId(),
                s.getPlanName(),
                s.getStatus(),
                s.getProvider() != null ? s.getProvider().name() : null,
                s.getStartedAt(),
                s.getExpiresAt(),
                ChronoUnit.DAYS.between(now, s.getExpiresAt()),
                autoRenew,
                s.getLockedPriceRub(),
                s.getRootPaymentId(),
                s.getRenewalNoticeSentAt(),
                s.getRenewalNoticeDeliveredAt(),
                s.getRenewalNoticeDeliveredAt() != null,
                s.getLastRenewalAttemptAt(),
                s.getRenewalFirstFailedAt(),
                s.getRenewalFirstFailedAt() != null ? s.getRenewalFirstFailedAt().plusDays(MAX_RETRY_DAYS) : null,
                autoRenew && !chargeable,
                s.getCancelledAt(),
                s.getCreatedAt()
        );
    }
}
