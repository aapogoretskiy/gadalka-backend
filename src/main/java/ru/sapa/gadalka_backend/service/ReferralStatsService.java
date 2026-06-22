package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.repository.PaymentRepository;
import ru.sapa.gadalka_backend.repository.ReferralEventRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Аналитика реферальной системы для административной панели.
 * <p>
 * Два среза:
 * <ul>
 *   <li><b>Маркетинг</b> — коды типа "telegram_channel1": открытия, новые пользователи,
 *       доход (раздельно в рублях и в Telegram Stars) и доля каждого источника в общем доходе</li>
 *   <li><b>Пользователи</b> — топ рефереров и список кого они пригласили</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ReferralStatsService {

    private final ReferralEventRepository referralEventRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;

    /**
     * Сводная аналитика для вкладки "Рефералы":
     * маркетинговые источники + топ пользователей-рефереров.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> buildStats() {
        return Map.of(
                "marketing",        buildMarketingStats(),
                "topUserReferrers", buildTopUserReferrers()
        );
    }

    /**
     * Список пользователей, которых пригласил конкретный реферер.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getInvitedUsers(Long referrerUserId) {
        List<Long> invitedUserIds = referralEventRepository.findInvitedUserIdsByReferrer(referrerUserId);
        if (invitedUserIds.isEmpty()) return List.of();

        List<User> users = userRepository.findAllById(invitedUserIds);

        // Сохраняем порядок (findAllById возвращает в произвольном порядке)
        Map<Long, User> byId = users.stream().collect(Collectors.toMap(User::getId, u -> u));

        return invitedUserIds.stream()
                .filter(byId::containsKey)
                .map(id -> {
                    User u = byId.get(id);
                    return Map.<String, Object>of(
                            "userId",       u.getId(),
                            "telegramId",   u.getTelegramId(),
                            "firstName",    u.getFirstName() != null ? u.getFirstName() : "",
                            "username",     u.getUsername() != null ? u.getUsername() : "",
                            "createdAt",    u.getCreatedAt()
                    );
                })
                .collect(Collectors.toList());
    }

    // ── Внутренние методы ─────────────────────────────────────────────────────

    private List<Map<String, Object>> buildMarketingStats() {
        List<Object[]> eventRows = referralEventRepository.findMarketingSourceStats();
        List<Object[]> revenueRows = paymentRepository.findRevenueByReferralSource();

        // source -> [rubMinor, stars]
        Map<String, long[]> revenueBySource = new HashMap<>();
        long totalRubMinor = 0L;
        long totalStars = 0L;
        for (Object[] row : revenueRows) {
            String source   = (String) row[0];
            long rubMinor    = toLong(row[1]);
            long stars       = toLong(row[2]);
            revenueBySource.put(source, new long[]{rubMinor, stars});
            totalRubMinor += rubMinor;
            totalStars    += stars;
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : eventRows) {
            String source   = (String) row[0];
            long appOpens   = toLong(row[1]);
            long newUsers   = toLong(row[2]);

            long[] revenue   = revenueBySource.getOrDefault(source, new long[]{0L, 0L});
            long rubMinor    = revenue[0];
            long stars       = revenue[1];

            // Доля источника в общем доходе — рубли и звёзды считаются раздельно,
            // т.к. это разные валюты и смешивать их в одном % некорректно.
            double pctRubRevenue   = totalRubMinor > 0 ? Math.round(rubMinor * 1000.0 / totalRubMinor) / 10.0 : 0.0;
            double pctStarsRevenue = totalStars > 0 ? Math.round(stars * 1000.0 / totalStars) / 10.0 : 0.0;

            result.add(Map.of(
                    "source",            source,
                    "appOpens",          appOpens,
                    "newUsers",          newUsers,
                    "revenueRub",        rubMinor / 100.0,
                    "pctRubRevenue",     pctRubRevenue,
                    "revenueStars",      stars,
                    "pctStarsRevenue",   pctStarsRevenue
            ));
        }
        return result;
    }

    private List<Map<String, Object>> buildTopUserReferrers() {
        List<Object[]> rows = referralEventRepository.findTopUserReferrers();
        if (rows.isEmpty()) return List.of();

        // Собираем ID всех рефереров и загружаем одним запросом
        List<Long> referrerIds = rows.stream()
                .map(r -> toLong(r[0]))
                .collect(Collectors.toList());

        Map<Long, User> usersById = userRepository.findAllById(referrerIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Long referrerId   = toLong(row[0]);
            long invitedCount = toLong(row[1]);
            User u = usersById.get(referrerId);
            if (u == null) continue;

            result.add(Map.of(
                    "userId",        u.getId(),
                    "telegramId",    u.getTelegramId(),
                    "firstName",     u.getFirstName() != null ? u.getFirstName() : "",
                    "username",      u.getUsername() != null ? u.getUsername() : "",
                    "invitedCount",  invitedCount
            ));
        }
        return result;
    }

    /** Безопасное приведение Number → long (PostgreSQL возвращает BigInteger/Long). */
    private long toLong(Object val) {
        if (val instanceof Number) return ((Number) val).longValue();
        return 0L;
    }
}
