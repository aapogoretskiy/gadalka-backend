package ru.sapa.gadalka_backend.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.sapa.gadalka_backend.api.dto.feedback.CloseTicketRequest;
import ru.sapa.gadalka_backend.api.dto.feedback.CloseTicketResponse;
import ru.sapa.gadalka_backend.api.dto.feedback.TicketDetailsResponse;
import ru.sapa.gadalka_backend.api.dto.feedback.TicketSummaryResponse;
import ru.sapa.gadalka_backend.bot.GadalkaTelegramBot;
import ru.sapa.gadalka_backend.domain.SupportTicket;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.type.CreditTransactionReason;
import ru.sapa.gadalka_backend.domain.type.SupportTicketStatus;
import ru.sapa.gadalka_backend.repository.FortuneCreditLogRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;
import ru.sapa.gadalka_backend.service.BroadcastService;
import ru.sapa.gadalka_backend.service.FortuneCreditService;
import ru.sapa.gadalka_backend.service.ReportService;
import ru.sapa.gadalka_backend.service.ReferralStatsService;
import ru.sapa.gadalka_backend.service.SupportTicketService;

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
    private final BroadcastService broadcastService;
    private final ReportService reportService;
    private final ReferralStatsService referralStatsService;
    private final SupportTicketService supportTicketService;

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
     * <p>Если пользователь пришёл по реферальной ссылке другого пользователя (referral_source = "ref_<telegramId>"),
     * дополнительно возвращается поле {@code referrerName} с именем реферера.
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

        String referrerName = StringUtils.EMPTY;
        String referralSource = user.getReferralSource() != null ? user.getReferralSource() : StringUtils.EMPTY;
        if (referralSource.startsWith("ref_")) {
            try {
                Long referrerTelegramId = Long.parseLong(referralSource.substring(4));
                referrerName = userRepository.findByTelegramId(referrerTelegramId)
                        .map(userByTgId -> {
                            String name = userByTgId.getFirstName() != null ? userByTgId.getFirstName() : StringUtils.EMPTY;
                            if (userByTgId.getUsername() != null) name += " (@" + userByTgId.getUsername() + ")";
                            return name.trim();
                        })
                        .orElse("Удалён");
            } catch (NumberFormatException ignored) { }
        }

        return ResponseEntity.ok(Map.ofEntries(
                Map.entry("id", user.getId()),
                Map.entry("telegramId", user.getTelegramId()),
                Map.entry("username", user.getUsername() != null ? user.getUsername() : ""),
                Map.entry("firstName", user.getFirstName() != null ? user.getFirstName() : ""),
                Map.entry("lastName", user.getLastName() != null ? user.getLastName() : ""),
                Map.entry("createdAt", user.getCreatedAt()),
                Map.entry("lastActiveAt", user.getLastActiveAt() != null ? user.getLastActiveAt() : ""),
                Map.entry("banned", user.isBanned()),
                Map.entry("referralSource", referralSource),
                Map.entry("referrerName", referrerName),
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

    /**
     * POST /api/admin/broadcast
     * Body: { "message": "...", "giftAmount": 5, "userIds": [1, 2, 3] }
     *
     * <p>Запускает массовую рассылку в фоновом потоке и сразу возвращает 200.
     * Если {@code userIds} пусто или null — рассылка по всем зарегистрированным пользователям.
     * Если {@code giftAmount} > 0 — каждому получателю начисляются знаки.
     */
    @PostMapping("/broadcast")
    public ResponseEntity<?> broadcast(@RequestBody BroadcastRequest body, HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("adminTelegramId");

        if (body.message() == null || body.message().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "message не может быть пустым"));
        }

        boolean toAll = body.userIds() == null || body.userIds().isEmpty();
        log.info("Admin {} запустил рассылку: toAll={}, giftAmount={}, recipients={}", adminId, toAll, body.giftAmount(), toAll ? "all" : body.userIds().size());

        broadcastService.broadcast(body.message(), body.giftAmount(), body.userIds());

        String info = toAll ? "всем пользователям" : "выбранным (" + body.userIds().size() + ")";
        return ResponseEntity.ok(Map.of("message", "Рассылка запущена " + info));
    }

    /**
     * GET /api/admin/referral-stats
     *
     * <p>Аналитика реферальной системы:
     * <ul>
     *   <li>Маркетинговые источники — клики, открытия, новые пользователи, конверсия</li>
     *   <li>Топ-50 пользователей по количеству приглашённых</li>
     * </ul>
     */
    @GetMapping("/referral-stats")
    public ResponseEntity<?> getReferralStats(HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("adminTelegramId");
        log.info("Admin {} запросил реферальную аналитику", adminId);
        return ResponseEntity.ok(referralStatsService.buildStats());
    }

    /**
     * GET /api/admin/users/{id}/invites
     *
     * <p>Список пользователей, которых пригласил реферер с указанным id.
     * Возвращает пустой массив если никого не приглашал.
     */
    @GetMapping("/users/{id}/invites")
    public ResponseEntity<?> getUserInvites(@PathVariable Long id, HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("adminTelegramId");
        log.info("Admin {} запросил список приглашённых userId={}", adminId, id);
        return ResponseEntity.ok(referralStatsService.getInvitedUsers(id));
    }

    /** DTO для запроса рассылки */
    record BroadcastRequest(String message, Integer giftAmount, List<Long> userIds) {}

    /**
     * GET /api/admin/reports
     *
     * <p>Возвращает агрегированную статистику для страницы отчётов:
     * пользователи (DAU/WAU/новые), гадания, платежи (RUB + Stars раздельно), знаки.
     */
    @GetMapping("/reports")
    public ResponseEntity<?> getReports(HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("adminTelegramId");
        log.info("Admin {} запросил отчёты", adminId);
        return ResponseEntity.ok(reportService.buildReport());
    }

    // ══════════════════════════════════════════════════════════
    //  SUPPORT TICKETS
    // ══════════════════════════════════════════════════════════

    /**
     * GET /api/admin/tickets?status=OPEN&page=0&size=20
     *
     * <p>Список заявок обратной связи с пагинацией.
     * Параметр {@code status} опционален: OPEN, CLOSED или отсутствует (все заявки).
     */
    @GetMapping("/tickets")
    public ResponseEntity<Page<TicketSummaryResponse>> getTickets(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        Long adminId = (Long) request.getAttribute("adminTelegramId");
        log.info("Admin {} запросил список заявок: status={}, page={}", adminId, status, page);

        SupportTicketStatus statusFilter = (status != null && !status.isBlank())
                ? SupportTicketStatus.valueOf(status.toUpperCase())
                : null;

        return ResponseEntity.ok(supportTicketService.getTickets(statusFilter, page, size)
                .map(this::toTicketSummary));
    }

    /**
     * GET /api/admin/tickets/{id}
     *
     * <p>Детальная информация о заявке: текст обращения, пользователь, статус, история.
     */
    @GetMapping("/tickets/{id}")
    public ResponseEntity<TicketDetailsResponse> getTicket(@PathVariable Long id, HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("adminTelegramId");
        log.info("Admin {} запросил заявку ticketId={}", adminId, id);

        return supportTicketService.getTicket(id)
                .flatMap(ticket -> userRepository.findById(ticket.getUserId())
                        .map(user -> ResponseEntity.ok(TicketDetailsResponse.from(ticket, user))))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/admin/tickets/{id}/close
     * Body: { "creditsToGift": 5 } — оба поля опциональны.
     *
     * <p>Закрывает заявку. Если {@code creditsToGift > 0} — начисляет знаки
     * и отправляет пользователю уведомление в Telegram.
     */
    @PostMapping("/tickets/{id}/close")
    public ResponseEntity<CloseTicketResponse> closeTicket(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) CloseTicketRequest body,
            HttpServletRequest request) {

        Long adminId = (Long) request.getAttribute("adminTelegramId");
        Integer creditsToGift = (body != null) ? body.creditsToGift() : null;

        return supportTicketService.closeTicket(id, adminId, creditsToGift)
                .map(ticket -> ResponseEntity.ok(new CloseTicketResponse(
                        "Заявка закрыта",
                        ticket.getId(),
                        ticket.getCreditsGifted() != null ? ticket.getCreditsGifted() : 0
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Краткое представление заявки для списка */
    private TicketSummaryResponse toTicketSummary(SupportTicket ticket) {
        String userName = userRepository.findById(ticket.getUserId())
                .map(u -> {
                    String name = u.getFirstName() != null ? u.getFirstName() : "";
                    if (u.getUsername() != null) name += " (@" + u.getUsername() + ")";
                    return name.trim();
                })
                .orElse("userId=" + ticket.getUserId());
        return TicketSummaryResponse.from(ticket, userName);
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
