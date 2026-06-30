package ru.sapa.gadalka_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.api.dto.admin.payment.TransactionDetailsDto;
import ru.sapa.gadalka_backend.api.dto.admin.payment.TransactionSummaryDto;
import ru.sapa.gadalka_backend.api.dto.admin.payment.WebhookInfoDto;
import ru.sapa.gadalka_backend.domain.Payment;
import ru.sapa.gadalka_backend.domain.PaymentProduct;
import ru.sapa.gadalka_backend.domain.PaymentWebhookLog;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.type.PaymentProvider;
import ru.sapa.gadalka_backend.domain.type.PaymentStatus;
import ru.sapa.gadalka_backend.repository.PaymentProductRepository;
import ru.sapa.gadalka_backend.repository.PaymentRepository;
import ru.sapa.gadalka_backend.repository.PaymentWebhookLogRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Логика вкладки "Транзакции" в админ-панели: список покупок знаков с фильтрами
 * и детальная карточка транзакции с сопоставленным webhook-подтверждением.
 * <p>
 * Вынесено из {@code AdminController} в отдельный сервис по тому же принципу,
 * что и {@link ReportService} / {@link SupportTicketService} — контроллер
 * остаётся тонким маршрутизатором, вся логика обогащения и matching здесь.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentProductRepository paymentProductRepository;
    private final PaymentWebhookLogRepository webhookLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /**
     * Список транзакций с опциональными фильтрами. Любой из параметров может быть {@code null} —
     * соответствующий фильтр тогда не применяется.
     *
     * @param search telegram_id (точное совпадение, если строка — число) или подстрока username
     */
    @Transactional(readOnly = true)
    public Page<TransactionSummaryDto> listTransactions(
            PaymentStatus status,
            PaymentProvider provider,
            String search,
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable) {

        Specification<Payment> spec = Specification.unrestricted();

        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (provider != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("provider"), provider));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }

        if (search != null && !search.isBlank()) {
            List<Long> userIds = resolveUserIds(search.trim());
            if (userIds.isEmpty()) {
                return Page.empty(pageable);
            }
            spec = spec.and((root, query, cb) -> root.get("userId").in(userIds));
        }

        Page<Payment> page = paymentRepository.findAll(spec, pageable);
        return enrich(page);
    }

    /** Детальная карточка транзакции с сопоставленным webhook-логом (если найден). */
    @Transactional(readOnly = true)
    public Optional<TransactionDetailsDto> getTransactionDetails(Long id) {
        return paymentRepository.findById(id).map(payment -> {
            User user = userRepository.findById(payment.getUserId()).orElse(null);
            PaymentProduct product = paymentProductRepository.findByCode(payment.getProductCode()).orElse(null);
            WebhookInfoDto webhook = findMatchingWebhook(payment);
            return TransactionDetailsDto.of(payment, user, product, webhook);
        });
    }

    /** Резолвит поисковую строку в список user.id — точный telegram_id или LIKE по username. */
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

    /** Батч-обогащение страницы платежей данными пользователя и продукта — без N+1. */
    private Page<TransactionSummaryDto> enrich(Page<Payment> page) {
        List<Long> userIds = page.getContent().stream().map(Payment::getUserId).distinct().toList();
        Map<Long, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        // Каталог продуктов небольшой (единицы записей) — читаем целиком и кэшируем на время запроса.
        Map<String, PaymentProduct> productsByCode = paymentProductRepository.findAll().stream()
                .collect(Collectors.toMap(PaymentProduct::getCode, Function.identity(), (a, b) -> a));

        return page.map(payment -> TransactionSummaryDto.from(
                payment,
                usersById.get(payment.getUserId()),
                productsByCode.get(payment.getProductCode())
        ));
    }

    /**
     * Пытается найти webhook-лог, относящийся к данной транзакции.
     * <p>
     * payment_webhook_log не имеет FK на payments — связь только через содержимое
     * raw_payload. Но оба провайдера кладут туда наш внутренний Payment.id:
     * ЮKassa — в {@code object.metadata.internal_payment_id} (мы сами это передаём
     * при создании платежа), Robokassa — в {@code invId} (наш Payment.id, который
     * мы передали как номер заказа). Сверяем точное совпадение, а не эвристику по времени —
     * окно по received_at используется только чтобы не сканировать весь лог целиком.
     * <p>
     * Telegram Stars подтверждается синхронно через Bot API, без отдельного webhook-лога —
     * для этого провайдера метод сразу возвращает {@code null}.
     */
    private WebhookInfoDto findMatchingWebhook(Payment payment) {
        if (payment.getProvider() == PaymentProvider.TELEGRAM_STARS) {
            return null;
        }

        OffsetDateTime from = payment.getCreatedAt().minusMinutes(5);
        OffsetDateTime to = (payment.getUpdatedAt() != null ? payment.getUpdatedAt() : OffsetDateTime.now()).plusDays(1);

        List<PaymentWebhookLog> candidates = webhookLogRepository
                .findAllByProviderAndReceivedAtBetweenOrderByReceivedAtDesc(payment.getProvider(), from, to);

        String targetInternalId = String.valueOf(payment.getId());
        for (PaymentWebhookLog candidate : candidates) {
            if (matchesPayment(candidate, payment.getProvider(), targetInternalId)) {
                return WebhookInfoDto.from(candidate);
            }
        }
        return null;
    }

    private boolean matchesPayment(PaymentWebhookLog webhookLog, PaymentProvider provider, String targetInternalId) {
        try {
            JsonNode node = objectMapper.readTree(webhookLog.getRawPayload());
            String candidateId = switch (provider) {
                case YOOKASSA  -> node.path("object").path("metadata").path("internal_payment_id").asText(null);
                case ROBOKASSA -> node.path("invId").asText(null);
                default        -> null;
            };
            return targetInternalId.equals(candidateId);
        } catch (Exception e) {
            log.warn("Не удалось разобрать raw_payload webhook-лога id={} при сопоставлении с транзакцией: {}",
                    webhookLog.getId(), e.getMessage());
            return false;
        }
    }
}
