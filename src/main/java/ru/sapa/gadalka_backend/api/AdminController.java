package ru.sapa.gadalka_backend.api;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.sapa.gadalka_backend.bot.GadalkaTelegramBot;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.type.CreditTransactionReason;
import ru.sapa.gadalka_backend.repository.FortuneCreditLogRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;
import ru.sapa.gadalka_backend.service.FortuneCreditService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Административные операции над пользователями.
 * Все эндпоинты защищены AdminFilter — туда попасть можно только с валидным Admin JWT
 * и telegramId из ENV-whitelist.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final FortuneCreditService fortuneCreditService;
    private final FortuneCreditLogRepository creditLogRepository;
    private final GadalkaTelegramBot telegramBot;

    /**
     * GET /api/admin/users?page=0&size=20&search=username_или_telegram_id
     *
     * <p>Список пользователей с пагинацией.
     * Поиск: если введено число — ищем по точному telegram_id,
     * иначе — по username (частичное совпадение без учёта регистра).
     */
    @GetMapping("/users")
    public ResponseEntity<?> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            HttpServletRequest request) {

        Long adminId = (Long) request.getAttribute("adminTelegramId");
        log.info("Admin {} запросил список пользователей: page={}, search={}", adminId, page, search);

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<User> users;
        if (search != null && !search.isBlank()) {
            String trimmed = search.trim();
            try {
                Long telegramId = Long.parseLong(trimmed);
                Optional<User> found = userRepository.findByTelegramId(telegramId);
                List<User> list = found.map(List::of).orElse(List.of());
                users = new PageImpl<>(list, pageable, list.size());
            } catch (NumberFormatException e) {
                // Иначе ищем по username
                users = userRepository.findByUsernameContainingIgnoreCase(trimmed, pageable);
            }
        } else {
            users = userRepository.findAll(pageable);
        }

        return ResponseEntity.ok(users.map(this::toSummary));
    }

    /**
     * GET /api/admin/users/{id}
     *
     * <p>Детальная информация о пользователе:
     * баланс, суммарно начислено/потрачено, реферальный источник, последняя активность.
     *
     * <p>Примечание: текущая реферальная система трекает маркетинговые кампании
     * (referral_source — откуда пришёл пользователь), а не пользователь-пользователь
     * приглашения. Счётчик приглашённых друзей — отдельная фича.
     */
    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUser(@PathVariable Long id, HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("adminTelegramId");
        log.info("Admin {} запросил детали пользователя: userId={}", adminId, id);

        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User user = userOpt.get();

        int balance = fortuneCreditService.getBalance(user.getId());

        var logs = creditLogRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId());
        int totalSpent = logs.stream()
                .filter(l -> l.getDelta() < 0)
                .mapToInt(l -> Math.abs(l.getDelta()))
                .sum();
        int totalGranted = logs.stream()
                .filter(l -> l.getDelta() > 0)
                .mapToInt(l -> l.getDelta())
                .sum();

        return ResponseEntity.ok(Map.ofEntries(
                Map.entry("id", user.getId()),
                Map.entry("telegramId", user.getTelegramId()),
                Map.entry("username", user.getUsername() != null ? user.getUsername() : ""),
                Map.entry("firstName", user.getFirstName() != null ? user.getFirstName() : ""),
                Map.entry("lastName", user.getLastName() != null ? user.getLastName() : ""),
                Map.entry("createdAt", user.getCreatedAt()),
                Map.entry("lastActiveAt", user.getLastActiveAt() != null ? user.getLastActiveAt() : ""),
                Map.entry("banned", user.isBanned()),
                Map.entry("referralSource", user.getReferralSource() != null ? user.getReferralSource() : ""),
                Map.entry("balance", balance),
                Map.entry("totalSpent", totalSpent),
                Map.entry("totalGranted", totalGranted)
        ));
    }

    /**
     * POST /api/admin/users/{id}/gift
     * Body: { "amount": 5 }
     *
     * <p>Начисляет кредиты пользователю и отправляет уведомление в бот.
     */
    @PostMapping("/users/{id}/gift")
    public ResponseEntity<?> giftCredits(@PathVariable Long id,
                                         @RequestBody Map<String, Integer> body,
                                         HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("adminTelegramId");
        Integer amount = body.get("amount");
        if (amount == null || amount <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "amount должен быть > 0"));
        }
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User user = userOpt.get();
        fortuneCreditService.grantCredits(user.getId(), amount, CreditTransactionReason.ADMIN_GIFT, null);
        telegramBot.sendGiftNotification(user.getTelegramId(), amount);
        log.info("Admin {} подарил {} кредитов пользователю userId={}", adminId, amount, id);
        return ResponseEntity.ok(Map.of("message", "Подарок успешно отправлен"));
    }

    /**
     * POST /api/admin/users/{id}/ban
     *
     * <p>Блокирует пользователя. JwtAuthFilter начнёт возвращать 403 при следующем запросе.
     */
    @PostMapping("/users/{id}/ban")
    public ResponseEntity<?> banUser(@PathVariable Long id, HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("adminTelegramId");
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User user = userOpt.get();
        user.setBanned(true);
        userRepository.save(user);
        log.info("Admin {} заблокировал пользователя userId={} (telegramId={})", adminId, id, user.getTelegramId());
        return ResponseEntity.ok(Map.of("message", "Пользователь заблокирован"));
    }

    /**
     * POST /api/admin/users/{id}/unban
     *
     * <p>Снимает блокировку с пользователя.
     */
    @PostMapping("/users/{id}/unban")
    public ResponseEntity<?> unbanUser(@PathVariable Long id, HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("adminTelegramId");
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User user = userOpt.get();
        user.setBanned(false);
        userRepository.save(user);
        log.info("Admin {} разблокировал пользователя userId={} (telegramId={})",
                adminId, id, user.getTelegramId());
        return ResponseEntity.ok(Map.of("message", "Пользователь разблокирован"));
    }

    /** Краткое представление пользователя для таблицы со списком */
    private Map<String, Object> toSummary(User user) {
        return Map.of(
                "id", user.getId(),
                "telegramId", user.getTelegramId(),
                "username", user.getUsername() != null ? user.getUsername() : "",
                "firstName", user.getFirstName() != null ? user.getFirstName() : "",
                "createdAt", user.getCreatedAt(),
                "lastActiveAt", user.getLastActiveAt() != null ? user.getLastActiveAt() : "",
                "banned", user.isBanned()
        );
    }
}
