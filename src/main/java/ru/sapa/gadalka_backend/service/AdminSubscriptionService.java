package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.api.dto.admin.subscription.AdminSubscriptionRowDto;
import ru.sapa.gadalka_backend.api.dto.admin.subscription.AdminSubscriptionStatsDto;
import ru.sapa.gadalka_backend.domain.Subscription;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.repository.SubscriptionRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Логика вкладки «Подписчики» в админ-панели: кто и на что подписан, что с автопродлением,
 * какие подписки требуют внимания.
 * <p>
 * Отдельно от вкладки «Подписки» (там {@link SubscriptionPlanAdminService} — справочник планов
 * и цены): здесь операционный список купленных подписок, у него другие фильтры и другая
 * частота обращения. Вынесено в сервис по тому же принципу, что и {@link AdminPaymentService} —
 * контроллер остаётся тонким.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSubscriptionService {

    /** Горизонт для счётчика «истекают скоро» */
    private static final int EXPIRING_SOON_DAYS = 7;

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    /**
     * Список подписок с опциональными фильтрами. Любой параметр может быть {@code null} —
     * соответствующий фильтр тогда не применяется.
     *
     * @param status       статус подписки; для ACTIVE дополнительно проверяется, что срок не истёк —
     *                     иначе в «действующие» попадали бы просроченные строки, которым статус
     *                     никто не менял (плановое истечение статус не трогает)
     * @param problemsOnly только требующие внимания: идут ретраи списания, списание зависло
     *                     в ожидании вебхука, либо автопродление включено, но уже невозможно
     * @param search       telegram_id (точное совпадение, если строка — число) или подстрока username
     */
    @Transactional(readOnly = true)
    public Page<AdminSubscriptionRowDto> list(
            String status,
            Long planId,
            Boolean autoRenew,
            boolean problemsOnly,
            String search,
            Pageable pageable) {

        OffsetDateTime now = OffsetDateTime.now();
        Specification<Subscription> spec = Specification.unrestricted();

        if (status != null && !status.isBlank()) {
            String value = status.trim().toUpperCase();
            spec = "ACTIVE".equals(value)
                    ? spec.and((root, query, cb) -> cb.and(
                            cb.equal(root.get("status"), "ACTIVE"),
                            cb.greaterThan(root.get("expiresAt"), now)))
                    : spec.and((root, query, cb) -> cb.equal(root.get("status"), value));
        }
        if (planId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("planId"), planId));
        }
        if (autoRenew != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("autoRenewEnabled"), autoRenew));
        }
        if (problemsOnly) {
            spec = spec.and((root, query, cb) -> cb.or(
                    root.get("status").in("SUSPENDED", "RENEWAL_PENDING"),
                    cb.and(
                            cb.equal(root.get("autoRenewEnabled"), true),
                            cb.or(
                                    cb.not(root.get("status").in("ACTIVE", "SUSPENDED", "RENEWAL_PENDING")),
                                    cb.and(
                                            cb.equal(root.get("status"), "ACTIVE"),
                                            cb.lessThanOrEqualTo(root.get("expiresAt"), now))))));
        }
        if (search != null && !search.isBlank()) {
            List<Long> userIds = resolveUserIds(search.trim());
            if (userIds.isEmpty()) {
                return Page.empty(pageable);
            }
            spec = spec.and((root, query, cb) -> root.get("userId").in(userIds));
        }

        return enrich(subscriptionRepository.findAll(spec, pageable), now);
    }

    /** Счётчики над таблицей — считаются запросами к БД, не по загруженной странице */
    @Transactional(readOnly = true)
    public AdminSubscriptionStatsDto stats() {
        OffsetDateTime now = OffsetDateTime.now();
        return new AdminSubscriptionStatsDto(
                subscriptionRepository.countActive(now),
                subscriptionRepository.countActiveWithAutoRenew(now),
                subscriptionRepository.countByStatus("SUSPENDED"),
                subscriptionRepository.countByStatus("RENEWAL_PENDING"),
                subscriptionRepository.countExpiringBefore(now, now.plusDays(EXPIRING_SOON_DAYS)),
                subscriptionRepository.countAutoRenewZombies(now),
                subscriptionRepository.sumAutoRenewLockedPrice(now));
    }

    /** Батч-обогащение страницы данными пользователей — без N+1 */
    private Page<AdminSubscriptionRowDto> enrich(Page<Subscription> page, OffsetDateTime now) {
        List<Long> userIds = page.getContent().stream().map(Subscription::getUserId).distinct().toList();
        Map<Long, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return page.map(s -> AdminSubscriptionRowDto.from(s, usersById.get(s.getUserId()), now));
    }

    private List<Long> resolveUserIds(String search) {
        try {
            Long telegramId = Long.parseLong(search);
            return userRepository.findByTelegramId(telegramId)
                    .map(u -> List.of(u.getId()))
                    .orElse(List.of());
        } catch (NumberFormatException e) {
            return userRepository.findIdsByUsernameContainingIgnoreCase(search);
        }
    }
}
