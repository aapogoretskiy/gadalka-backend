package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.api.dto.admin.AdminSubscriptionPlanDto;
import ru.sapa.gadalka_backend.bot.GadalkaTelegramBot;
import ru.sapa.gadalka_backend.domain.Subscription;
import ru.sapa.gadalka_backend.domain.SubscriptionPlan;
import ru.sapa.gadalka_backend.domain.SubscriptionPlanQuota;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.type.QuotaPeriod;
import ru.sapa.gadalka_backend.repository.SubscriptionPlanQuotaRepository;
import ru.sapa.gadalka_backend.repository.SubscriptionPlanRepository;
import ru.sapa.gadalka_backend.repository.SubscriptionRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static ru.sapa.gadalka_backend.constant.SystemConfigConstants.STARS_RUB_RATE_KOPECKS;

/**
 * CRUD планов подписки для админ-панели.
 * <p>
 * Редактирование плана НЕ влияет на уже купленные подписки — их квоты
 * заснапшочены в subscription_quotas при активации (см. SubscriptionActivationService).
 * Поэтому здесь можно свободно менять цены, квоты и деактивировать планы.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionPlanAdminService {

    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionPlanQuotaRepository planQuotaRepository;
    private final SystemConfigService systemConfigService;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final GadalkaTelegramBot telegramBot;

    /** Все планы (включая неактивные) с квотами — для таблицы в админке. */
    @Transactional(readOnly = true)
    public List<AdminSubscriptionPlanDto> getAllPlans() {
        List<SubscriptionPlan> plans = planRepository.findAllByOrderBySortOrderAsc();
        if (plans.isEmpty()) return List.of();

        Map<Long, List<SubscriptionPlanQuota>> quotasByPlan = planQuotaRepository
                .findAllByPlanIdIn(plans.stream().map(SubscriptionPlan::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(SubscriptionPlanQuota::getPlanId));

        return plans.stream()
                .map(plan -> AdminSubscriptionPlanDto.from(plan, toQuotaDtos(quotasByPlan.get(plan.getId()))))
                .toList();
    }

    /** Создаёт план вместе с квотами. Новый план по умолчанию создаётся АКТИВНЫМ, если так пришло с фронта. */
    @Transactional
    public AdminSubscriptionPlanDto createPlan(AdminSubscriptionPlanDto dto) {
        validate(dto);

        SubscriptionPlan plan = planRepository.save(SubscriptionPlan.builder()
                .name(dto.name().trim())
                .priceRub(dto.priceRub())
                .priceStars(dto.priceStars())
                .durationDays(dto.durationDays())
                .isActive(dto.isActive())
                .sortOrder(dto.sortOrder())
                .build());

        saveQuotas(plan.getId(), dto.quotas());

        log.info("Создан план подписки: id={}, name='{}', priceRub={}, квот={}",
                plan.getId(), plan.getName(), plan.getPriceRub(), dto.quotas().size());
        return AdminSubscriptionPlanDto.from(plan, dto.quotas());
    }

    /** Обновляет план и ПОЛНОСТЬЮ заменяет его квоты (простая семантика для админки). */
    @Transactional
    public AdminSubscriptionPlanDto updatePlan(Long planId, AdminSubscriptionPlanDto dto) {
        validate(dto);

        SubscriptionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("План не найден: id=" + planId));

        int oldPriceRub = plan.getPriceRub();

        plan.setName(dto.name().trim());
        plan.setPriceRub(dto.priceRub());
        plan.setPriceStars(dto.priceStars());
        plan.setDurationDays(dto.durationDays());
        plan.setIsActive(dto.isActive());
        plan.setSortOrder(dto.sortOrder());
        planRepository.save(plan);

        planQuotaRepository.deleteAllByPlanId(planId);
        saveQuotas(planId, dto.quotas());

        log.info("Обновлён план подписки: id={}, name='{}', active={}, квот={}",
                planId, plan.getName(), plan.getIsActive(), dto.quotas().size());

        // Цена уже подключённых к автопродлению подписчиков зафиксирована (locked_price_rub,
        // см. Subscription) и новой ценой плана не затрагивается — но по п. 6.14.2 соглашения
        // их всё равно нужно уведомить о самом факте изменения (вдруг цена упала и человек
        // захочет переоформиться на неё вручную).
        if (oldPriceRub != dto.priceRub()) {
            notifyAutoRenewSubscribersOfPriceChange(plan, oldPriceRub, dto.priceRub());
        }

        return AdminSubscriptionPlanDto.from(plan, dto.quotas());
    }

    /** Разовая рассылка активным auto-renew подписчикам плана о смене его цены (п. 6.14.2). */
    private void notifyAutoRenewSubscribersOfPriceChange(SubscriptionPlan plan, int oldPriceRub, int newPriceRub) {
        List<Subscription> subscribers = subscriptionRepository.findActiveAutoRenewByPlanId(plan.getId());
        if (subscribers.isEmpty()) return;

        int notified = 0;
        for (Subscription subscription : subscribers) {
            User user = userRepository.findById(subscription.getUserId()).orElse(null);
            if (user == null || user.isBanned()) continue;

            // lockedPriceRub у подписчика мог отличаться от прежней цены плана (например,
            // он подписался ещё раньше, до предыдущего изменения цены) — уведомляем именно
            // о его реальной зафиксированной цене, а не о том, что было в плане секунду назад.
            int lockedPriceRub = subscription.getLockedPriceRub() != null ? subscription.getLockedPriceRub() : oldPriceRub;
            if (telegramBot.sendPlanPriceChangedNotice(user.getTelegramId(), plan.getName(), lockedPriceRub, newPriceRub)) {
                notified++;
            }
        }

        log.info("Уведомление об изменении цены плана '{}': подписчиков={}, уведомлено={}",
                plan.getName(), subscribers.size(), notified);
    }

    /** Курс «копеек за звезду» для автоподсказки цены в Stars. Дефолт — из миграции V65. */
    public int getStarsRateKopecks() {
        return systemConfigService.getIntValue(STARS_RUB_RATE_KOPECKS, 133);
    }

    public void updateStarsRateKopecks(int rate) {
        if (rate <= 0) throw new IllegalArgumentException("Курс должен быть положительным");
        systemConfigService.setValue(STARS_RUB_RATE_KOPECKS, String.valueOf(rate));
        log.info("Обновлён курс Stars: {} коп./звезда", rate);
    }

    // ──────────────────────────────────────────────

    private void saveQuotas(Long planId, List<AdminSubscriptionPlanDto.QuotaDto> quotas) {
        for (AdminSubscriptionPlanDto.QuotaDto q : quotas) {
            planQuotaRepository.save(SubscriptionPlanQuota.builder()
                    .planId(planId)
                    .featureType(q.featureType())
                    .quotaCount(q.quotaCount())
                    .quotaPeriod(q.unlimited() ? QuotaPeriod.DAILY : q.quotaPeriod())
                    .isUnlimited(q.unlimited())
                    .build());
        }
    }

    private List<AdminSubscriptionPlanDto.QuotaDto> toQuotaDtos(List<SubscriptionPlanQuota> quotas) {
        if (quotas == null) return List.of();
        return quotas.stream()
                .map(q -> new AdminSubscriptionPlanDto.QuotaDto(q.getFeatureType(), q.getQuotaCount(),
                        q.getQuotaPeriod(), Boolean.TRUE.equals(q.getIsUnlimited())))
                .toList();
    }

    private void validate(AdminSubscriptionPlanDto dto) {
        if (dto.name() == null || dto.name().isBlank()) {
            throw new IllegalArgumentException("Название подписки не может быть пустым");
        }
        if (dto.priceRub() <= 0 || dto.priceStars() <= 0) {
            throw new IllegalArgumentException("Цена подписки должна быть положительной (в копейках и в звёздах)");
        }
        if (dto.durationDays() <= 0) {
            throw new IllegalArgumentException("Срок действия подписки должен быть положительным (в днях)");
        }
        if (dto.quotas() == null || dto.quotas().isEmpty()) {
            throw new IllegalArgumentException("У подписки должна быть хотя бы одна квота");
        }
        Set<Object> seen = new HashSet<>();
        for (AdminSubscriptionPlanDto.QuotaDto q : dto.quotas()) {
            if (q.featureType() == null || q.quotaPeriod() == null) {
                throw new IllegalArgumentException("У квоты должны быть указаны функция и периодичность");
            }
            if (q.quotaCount() <= 0) {
                throw new IllegalArgumentException("Количество в квоте должно быть положительным");
            }
            if (!seen.add(q.featureType())) {
                throw new IllegalArgumentException("Функция " + q.featureType() + " указана в квотах дважды");
            }
        }
    }
}
