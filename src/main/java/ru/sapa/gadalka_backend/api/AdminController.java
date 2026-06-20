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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.api.dto.admin.FeatureCostsDto;
import ru.sapa.gadalka_backend.api.dto.admin.report.AdminReportDto;
import ru.sapa.gadalka_backend.api.dto.feedback.CloseTicketRequest;
import ru.sapa.gadalka_backend.api.dto.feedback.CloseTicketResponse;
import ru.sapa.gadalka_backend.api.dto.feedback.TicketDetailsResponse;
import ru.sapa.gadalka_backend.api.dto.feedback.TicketSummaryResponse;
import ru.sapa.gadalka_backend.bot.GadalkaTelegramBot;
import ru.sapa.gadalka_backend.domain.*;
import ru.sapa.gadalka_backend.domain.type.CreditTransactionReason;
import ru.sapa.gadalka_backend.domain.type.FeedbackTargetType;
import ru.sapa.gadalka_backend.domain.type.SupportTicketStatus;
import ru.sapa.gadalka_backend.repository.ActionFeedbackRepository;
import ru.sapa.gadalka_backend.repository.CompatibilityReadingRepository;
import ru.sapa.gadalka_backend.repository.DailyCardRepository;
import ru.sapa.gadalka_backend.repository.FortuneRepository;
import ru.sapa.gadalka_backend.repository.FortuneCreditLogRepository;
import ru.sapa.gadalka_backend.repository.NumerologyDayReadingRepository;
import ru.sapa.gadalka_backend.repository.NumerologyWeekReadingRepository;
import ru.sapa.gadalka_backend.repository.UserProfileRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;
import ru.sapa.gadalka_backend.service.BroadcastService;
import ru.sapa.gadalka_backend.service.FeatureCostService;
import ru.sapa.gadalka_backend.service.FortuneCreditService;
import ru.sapa.gadalka_backend.service.ReportService;
import ru.sapa.gadalka_backend.service.ReferralStatsService;
import ru.sapa.gadalka_backend.service.SupportTicketService;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt", "lastActiveAt", "username", "firstName", "visitCount", "totalActionsCount", "totalSpent"
    );

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final FortuneCreditService fortuneCreditService;
    private final FortuneCreditLogRepository creditLogRepository;
    private final FortuneRepository fortuneRepository;
    private final CompatibilityReadingRepository compatibilityReadingRepository;
    private final NumerologyDayReadingRepository numerologyDayReadingRepository;
    private final NumerologyWeekReadingRepository numerologyWeekReadingRepository;
    private final DailyCardRepository dailyCardRepository;
    private final ActionFeedbackRepository actionFeedbackRepository;
    private final GadalkaTelegramBot telegramBot;
    private final BroadcastService broadcastService;
    private final ReportService reportService;
    private final ReferralStatsService referralStatsService;
    private final SupportTicketService supportTicketService;
    private final FeatureCostService featureCostService;

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
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            HttpServletRequest request) {

        Long adminId = (Long) request.getAttribute("adminTelegramId");
        log.info("Admin {} запросил список пользователей: page={}, search={}, sortBy={}, sortDir={}",
                adminId,
                page,
                search,
                sortBy,
                sortDir);

        // Защита от произвольных имён полей
        String safeField = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        boolean sortByLastActive = "lastActiveAt".equals(safeField);
        // totalSpent — агрегат из fortune_credit_log, не колонка User, поэтому сортируется
        // отдельным native-запросом (как и lastActiveAt), а не через Sort.by(...)
        boolean sortByTotalSpent = "totalSpent".equals(safeField);
        boolean customSort = sortByLastActive || sortByTotalSpent;

        Sort sort = customSort ? Sort.unsorted() : Sort.by(direction, safeField);
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), sort);

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
                if (sortByLastActive) {
                    PageRequest unsortedPageable = PageRequest.of(page, Math.min(size, 100));
                    users = direction == Sort.Direction.DESC
                            ? userRepository.findByUsernameOrderByLastActiveAtDesc(trimmed, unsortedPageable)
                            : userRepository.findByUsernameOrderByLastActiveAtAsc(trimmed, unsortedPageable);
                } else if (sortByTotalSpent) {
                    PageRequest unsortedPageable = PageRequest.of(page, Math.min(size, 100));
                    users = direction == Sort.Direction.DESC
                            ? userRepository.findByUsernameOrderByTotalSpentDesc(trimmed, unsortedPageable)
                            : userRepository.findByUsernameOrderByTotalSpentAsc(trimmed, unsortedPageable);
                } else {
                    users = userRepository.findByUsernameContainingIgnoreCase(trimmed, pageable);
                }
            }
        } else {
            if (sortByLastActive) {
                PageRequest unsortedPageable = PageRequest.of(page, Math.min(size, 100));
                users = direction == Sort.Direction.DESC
                        ? userRepository.findAllOrderByLastActiveAtDesc(unsortedPageable)
                        : userRepository.findAllOrderByLastActiveAtAsc(unsortedPageable);
            } else if (sortByTotalSpent) {
                PageRequest unsortedPageable = PageRequest.of(page, Math.min(size, 100));
                users = direction == Sort.Direction.DESC
                        ? userRepository.findAllOrderByTotalSpentDesc(unsortedPageable)
                        : userRepository.findAllOrderByTotalSpentAsc(unsortedPageable);
            } else {
                users = userRepository.findAll(pageable);
            }
        }

        // Батч-подсчёт потраченных знаков для пользователей текущей страницы —
        // один запрос вместо N, независимо от того, по какому полю сортируем.
        List<Long> userIds = users.getContent().stream().map(User::getId).collect(Collectors.toList());
        Map<Long, Long> spentByUserId = creditLogRepository.sumSpentByUserIds(userIds).stream()
                .collect(Collectors.toMap(FortuneCreditLogRepository.UserSpentRow::getUserId,
                        FortuneCreditLogRepository.UserSpentRow::getSpent));

        return ResponseEntity.ok(users.map(user -> toSummary(user, spentByUserId.getOrDefault(user.getId(), 0L))));
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
                .mapToInt(FortuneCreditLogEntry::getDelta)
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

        // Дата рождения из профиля (для отображения возраста в панели)
        var profileOpt = userProfileRepository.findByUserId(user.getId());
        String birthDate = profileOpt.filter(p -> p.getBirthDate() != null)
                .map(p -> p.getBirthDate().toString())
                .orElse(StringUtils.EMPTY);

        return ResponseEntity.ok(Map.ofEntries(
                Map.entry("id", user.getId()),
                Map.entry("telegramId", user.getTelegramId()),
                Map.entry("username", user.getUsername() != null ? user.getUsername() : ""),
                Map.entry("firstName", user.getFirstName() != null ? user.getFirstName() : ""),
                Map.entry("lastName", user.getLastName() != null ? user.getLastName() : ""),
                Map.entry("createdAt", user.getCreatedAt()),
                Map.entry("lastActiveAt", user.getLastActiveAt() != null ? user.getLastActiveAt() : ""),
                Map.entry("banned", user.isBanned()),
                Map.entry("premium", user.isPremium()),
                Map.entry("visitCount", user.getVisitCount()),
                Map.entry("birthDate", birthDate),
                Map.entry("referralSource", referralSource),
                Map.entry("referrerName", referrerName),
                Map.entry("balance", balance),
                Map.entry("totalSpent", totalSpent),
                Map.entry("totalGranted", totalGranted)
        ));
    }

    /**
     * GET /api/admin/users/{id}/actions?limit=30
     *
     * <p>Lazy-загрузка истории действий пользователя.
     * Возвращает последние N записей из всех таблиц активности (гадания, совместимость,
     * нумерология, карта дня), отсортированных по дате убыванию.
     * Запрашивается только при раскрытии соответствующего раздела в панели.
     */
    @Transactional(readOnly = true)
    @GetMapping("/users/{id}/actions")
    public ResponseEntity<?> getUserActions(
            @PathVariable Long id,
            @RequestParam(defaultValue = "30") int limit,
            HttpServletRequest request) {

        Long adminId = (Long) request.getAttribute("adminTelegramId");
        log.info("Admin {} запросил историю действий userId={}, limit={}", adminId, id, limit);

        if (userRepository.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        int safeLimit = Math.min(limit, 100);
        PageRequest pageable = PageRequest.of(0, safeLimit);

        var actions = new ArrayList<Map<String, Object>>();

        // ── Гадания (расклады) ────────────────────────────────────────────────
        List<Fortune> fortunes = fortuneRepository.findByUserIdOrderByCreatedAtDesc(id, pageable);
        List<Long> fortuneIds = fortunes
                .stream()
                .map(Fortune::getId)
                .collect(Collectors.toList());

        // Батч-загрузка фидбэков для гаданий (один запрос вместо N)
        Map<Long, ActionFeedback> fortuneFeedbacks = actionFeedbackRepository
                .findByActionTypeAndActionIdIn(FeedbackTargetType.FORTUNE, fortuneIds)
                .stream()
                .collect(Collectors.toMap(ActionFeedback::getActionId, fb -> fb));

        for (Fortune fortune : fortunes) {
            String type = fortune.getSpreadType() != null ? fortune.getSpreadType().name() : "THREE_CARD";
            String question = fortune.getQuestion();
            ActionFeedback fb = fortuneFeedbacks.get(fortune.getId());
            var item = new java.util.LinkedHashMap<String, Object>();
            item.put("id",             fortune.getId());
            item.put("type",           "FORTUNE_" + type);
            item.put("label",          fortuneLabel(type));
            item.put("date",           fortune.getCreatedAt().toString());
            item.put("details",        question.length() > 60 ? question.substring(0, 60) + "…" : question);
            item.put("interpretation", fortune.getInterpretation());
            item.put("feedbackRating", fb != null ? fb.getRating().name() : null);
            item.put("feedbackComment", fb != null ? fb.getComment() : null);
            actions.add(item);
        }

        // ── Совместимость ────────────────────────────────────────────────────
        List<CompatibilityReading> readings = compatibilityReadingRepository
                .findByUserIdOrderByCreatedAtDesc(id, pageable);
        List<Long> readingIds = readings
                .stream()
                .map(CompatibilityReading::getId)
                .collect(Collectors.toList());

        Map<Long, ActionFeedback> compatFeedbacks = actionFeedbackRepository
                .findByActionTypeAndActionIdIn(FeedbackTargetType.COMPATIBILITY, readingIds)
                .stream()
                .collect(Collectors.toMap(ActionFeedback::getActionId, fb -> fb));

        for (CompatibilityReading cr : readings) {
            ActionFeedback fb = compatFeedbacks.get(cr.getId());
            var item = new java.util.LinkedHashMap<String, Object>();
            item.put("id",             cr.getId());
            item.put("type",           "COMPATIBILITY");
            item.put("label",          "Совместимость");
            item.put("date",           cr.getCreatedAt().toString());
            item.put("details",        cr.getLabel() + " — " + cr.getScore() + "%");
            item.put("interpretation", cr.getInterpretation());
            item.put("feedbackRating", fb != null ? fb.getRating().name() : null);
            item.put("feedbackComment", fb != null ? fb.getComment() : null);
            actions.add(item);
        }

        // ── Нумерология ─────────────────────────────────────────────────────
        for (NumerologyDayReading nr : numerologyDayReadingRepository.findByUserIdOrderByDateDesc(id, pageable)) {
            var item = new java.util.LinkedHashMap<String, Object>();
            item.put("id",             nr.getId());
            item.put("type",           "NUMEROLOGY");
            item.put("label",          "Число дня");
            item.put("date",           nr.getDate().toString());
            item.put("details",        "Код дня: " + nr.getDayCode());
            item.put("interpretation", nr.getAffirmation());
            item.put("feedbackRating", null);
            item.put("feedbackComment", null);
            actions.add(item);
        }

        // ── Расклад на неделю ────────────────────────────────────────────────
        for (NumerologyWeekReading nwr : numerologyWeekReadingRepository.findByUserIdOrderByCreatedAtDesc(id, pageable)) {
            var item = new java.util.LinkedHashMap<String, Object>();
            item.put("id",             nwr.getId());
            item.put("type",           "NUMEROLOGY_WEEK");
            item.put("label",          "Расклад на неделю");
            item.put("date",           nwr.getCreatedAt().toString());
            item.put("details",        "Число недели: " + nwr.getWeekNumber());
            item.put("interpretation", null);
            item.put("feedbackRating", null);
            item.put("feedbackComment", null);
            actions.add(item);
        }

        // ── Карта дня ────────────────────────────────────────────────────────
        for (DailyCard dc : dailyCardRepository.findByUserIdOrderByDateDesc(id, pageable)) {
            String cardName = dc.getCard() != null ? dc.getCard().getName() : "—";
            var item = new java.util.LinkedHashMap<String, Object>();
            item.put("id",             dc.getId());
            item.put("type",           "DAILY_CARD");
            item.put("label",          "Карта дня");
            item.put("date",           dc.getDate().toString());
            item.put("details",        cardName);
            item.put("interpretation", null);
            item.put("feedbackRating", null);
            item.put("feedbackComment", null);
            actions.add(item);
        }

        // Сортируем общий список по дате убыванию и обрезаем до limit
        actions.sort(Comparator.comparing(a -> (String) a.get("date"), Comparator.reverseOrder()));
        var result = actions.size() > safeLimit ? actions.subList(0, safeLimit) : actions;

        return ResponseEntity.ok(result);
    }

    /** Человекочитаемая метка типа расклада */
    private String fortuneLabel(String spreadType) {
        return switch (spreadType) {
            case "THREE_CARD"   -> "Расклад 3 карты";
            case "HORSESHOE"    -> "Подкова";
            case "CELTIC_CROSS" -> "Кельтский крест";
            default -> "Расклад";
        };
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
     * Content-Type: multipart/form-data
     *
     * <p>Части запроса:
     * <ul>
     *   <li>{@code data} — JSON-строка с полями message, giftAmount, userIds, onlyAdmins</li>
     *   <li>{@code photo} — опциональный файл изображения</li>
     * </ul>
     *
     * <p>Запускает массовую рассылку в фоновом потоке и сразу возвращает 200.
     * Приоритет аудитории: userIds > onlyAdmins > все пользователи.
     * Если {@code giftAmount} > 0 — каждому получателю начисляются знаки.
     */
    @PostMapping(value = "/broadcast", consumes = "multipart/form-data")
    public ResponseEntity<?> broadcast(
            @RequestPart("data") BroadcastRequest body,
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            HttpServletRequest request) throws java.io.IOException {

        Long adminId = (Long) request.getAttribute("adminTelegramId");

        if (body.message() == null || body.message().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "message не может быть пустым"));
        }

        byte[] photoBytes = (photo != null && !photo.isEmpty()) ? photo.getBytes() : null;
        String photoFileName = (photo != null && !photo.isEmpty()) ? photo.getOriginalFilename() : null;

        boolean toAdmins = Boolean.TRUE.equals(body.onlyAdmins());
        boolean toSelected = body.userIds() != null && !body.userIds().isEmpty();
        boolean toAll = !toAdmins && !toSelected;

        log.info("Admin {} запустил рассылку: toAll={}, toAdmins={}, toSelected={}, giftAmount={}, hasPhoto={}",
                adminId,
                toAll,
                toAdmins,
                toSelected ? body.userIds().size() : 0,
                body.giftAmount(),
                photoBytes != null);

        broadcastService.broadcast(body.message(), body.giftAmount(), body.userIds(), photoBytes, photoFileName, toAdmins);

        String info = toAdmins ? "администраторам" : toSelected ? "выбранным (" + body.userIds().size() + ")" : "всем пользователям";
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

    /**
     * DTO для JSON-части запроса рассылки (part "data" в multipart/form-data).
     * Фото передаётся отдельной частью "photo", а не через это DTO.
     *
     * @param onlyAdmins если true — рассылка только администраторам из ADMIN_TELEGRAM_IDS
     */
    record BroadcastRequest(String message, Integer giftAmount, List<Long> userIds, Boolean onlyAdmins) {}

    /**
     * GET /api/admin/reports
     *
     * <p>Возвращает агрегированную статистику для страницы отчётов:
     * пользователи (DAU/WAU/новые), гадания, платежи (RUB + Stars раздельно), знаки.
     */
    @GetMapping("/reports")
    public ResponseEntity<AdminReportDto> getReports(HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("adminTelegramId");
        log.info("Admin {} запросил отчёты", adminId);
        return ResponseEntity.ok(reportService.buildReport());
    }

    /**
     * GET /api/admin/reports/range?from=YYYY-MM-DD&to=YYYY-MM-DD
     *
     * <p>Отчёт за произвольный диапазон дат (включительно).
     * Даты трактуются в московском часовом поясе (Europe/Moscow).
     * Параметр from не должен быть позже to.
     */
    @GetMapping("/reports/range")
    public ResponseEntity<?> getRangeReport(
            @RequestParam String from,
            @RequestParam String to,
            HttpServletRequest request) {

        Long adminId = (Long) request.getAttribute("adminTelegramId");

        LocalDateTime fromDate;
        LocalDateTime toDate;
        try {
            fromDate = LocalDateTime.parse(from);
            toDate   = LocalDateTime.parse(to);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Некорректный формат даты. Используйте YYYY-MM-DDTHH:mm:ss"));
        }

        if (fromDate.isAfter(toDate)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Дата начала не может быть позже даты окончания"));
        }

        log.info("Admin {} запросил отчёт за диапазон: {} — {}", adminId, fromDate, toDate);
        return ResponseEntity.ok(reportService.buildRangeReport(fromDate, toDate));
    }

    // ══════════════════════════════════════════════════════════
    //  СТОИМОСТЬ ПЛАТНЫХ ФУНКЦИЙ
    // ══════════════════════════════════════════════════════════

    /**
     * GET /api/admin/feature-costs
     *
     * <p>Текущая стоимость платных функций в знаках (читается из system_config).
     */
    @GetMapping("/feature-costs")
    public ResponseEntity<FeatureCostsDto> getFeatureCosts(HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("adminTelegramId");
        log.info("Admin {} запросил текущие цены платных функций", adminId);
        return ResponseEntity.ok(featureCostService.getAllCosts());
    }

    /**
     * PUT /api/admin/feature-costs
     * Body: { "threeCard": 3, "horseshoe": 6, "celticCross": 9, "compatibilityUnlock": 3, "numerologyWeek": 3 }
     *
     * <p>Обновляет стоимость всех платных функций сразу. Изменения вступают в силу немедленно
     * (без деплоя) — следующее списание знаков будет по новой цене.
     */
    @PutMapping("/feature-costs")
    public ResponseEntity<?> updateFeatureCosts(@RequestBody FeatureCostsDto body, HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("adminTelegramId");
        log.info("Admin {} обновляет цены платных функций: {}", adminId, body);

        try {
            featureCostService.updateCosts(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }

        return ResponseEntity.ok(featureCostService.getAllCosts());
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

    /** Краткое представление пользователя для таблицы со списком (без подсчёта потраченных знаков) */
    private Map<String, Object> toSummary(User user) {
        return toSummary(user, 0L);
    }

    /**
     * Краткое представление пользователя для таблицы со списком.
     * spentCredits передаётся отдельно, так как это агрегат из fortune_credit_log,
     * посчитанный батчем на уровне страницы (см. getUsers()), а не поле сущности User.
     * Map.of() имеет лимит в 10 пар "ключ-значение" — при добавлении totalSpent
     * пришлось перейти на Map.ofEntries(...).
     */
    private Map<String, Object> toSummary(User user, long spentCredits) {
        return Map.ofEntries(
                Map.entry("id", user.getId()),
                Map.entry("telegramId", user.getTelegramId()),
                Map.entry("username", user.getUsername() != null ? user.getUsername() : ""),
                Map.entry("firstName", user.getFirstName() != null ? user.getFirstName() : ""),
                Map.entry("createdAt", user.getCreatedAt()),
                Map.entry("lastActiveAt", user.getLastActiveAt() != null ? user.getLastActiveAt() : ""),
                Map.entry("banned", user.isBanned()),
                Map.entry("premium", user.isPremium()),
                Map.entry("visitCount", user.getVisitCount()),
                Map.entry("totalActionsCount", user.getTotalActionsCount()),
                Map.entry("totalSpent", spentCredits)
        );
    }
}
