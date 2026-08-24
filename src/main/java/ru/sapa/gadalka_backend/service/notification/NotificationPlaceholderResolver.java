package ru.sapa.gadalka_backend.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.constant.SystemConfigConstants;
import ru.sapa.gadalka_backend.domain.PaymentProduct;
import ru.sapa.gadalka_backend.domain.SubscriptionPlan;
import ru.sapa.gadalka_backend.domain.type.SpreadType;
import ru.sapa.gadalka_backend.repository.PaymentProductRepository;
import ru.sapa.gadalka_backend.repository.SubscriptionPlanRepository;
import ru.sapa.gadalka_backend.service.FeatureCostService;
import ru.sapa.gadalka_backend.service.SystemConfigService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Подстановка актуальных цен и стоимостей в тексты рассылки.
 *
 * <p>Зачем. Цены живут в БД и правятся из админки: пакеты знаков — в
 * {@code payment_products}, планы подписки — в {@code subscription_plans},
 * стоимость фич в знаках — в {@code system_config}. Если оставить цифры прямо
 * в тексте сообщения, первое же изменение цены превратит рассылку в недостоверную
 * рекламу — а это прямое нарушение ст. 5 ФЗ «О рекламе». Поэтому в каталоге стоят
 * плейсхолдеры, а конкретные значения подставляются в момент отправки.
 *
 * <p>Снимок значений собирается ОДИН раз на всю рассылку ({@link #snapshot()}),
 * а не на каждого пользователя — иначе на базе в несколько тысяч человек мы бы
 * сделали лишние тысячи запросов к БД.
 *
 * <p>Планы подписки ищутся по имени ({@code Lite}, {@code Premium}, {@code Superb}),
 * потому что поля {@code code} у планов нет. Если план переименовали, отключили,
 * или подписки в целом выключены тогглом {@code SUBSCRIPTIONS_AVAILABLE_FOR_ALL_USERS} —
 * соответствующие плейсхолдеры просто не попадут в снимок, и сообщения с ними будут
 * пропущены (см. {@link #apply(String, Map)}). Так мы никогда не отправим текст
 * с сырым «{price_lite}» или ссылку на то, что нельзя купить.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPlaceholderResolver {

    /** Плейсхолдер имени — подставляется отдельно, персонально для каждого получателя. */
    public static final String NAME_PLACEHOLDER = "{name}";

    private final PaymentProductRepository productRepository;
    private final SubscriptionPlanRepository planRepository;
    private final FeatureCostService featureCostService;
    private final SystemConfigService systemConfigService;

    /**
     * Собирает снимок всех подставляемых значений на текущий момент.
     *
     * @return карта «плейсхолдер → значение»; плейсхолдеры недоступных сущностей
     *         в карту не попадают
     */
    @Transactional(readOnly = true)
    public Map<String, String> snapshot() {
        Map<String, String> values = new HashMap<>();

        addPackValues(values, "PACK_3", "pack3");
        addPackValues(values, "PACK_7", "pack7");
        addPackValues(values, "PACK_15", "pack15");

        if (subscriptionsAvailable()) {
            List<SubscriptionPlan> plans = planRepository.findAllByIsActiveTrueOrderBySortOrderAsc();
            addPlanValues(values, plans, "Lite", "lite");
            addPlanValues(values, plans, "Premium", "premium");
            addPlanValues(values, plans, "Superb", "superb");
        }

        addCostValues(values);
        return values;
    }

    /**
     * Подставляет значения в текст.
     *
     * @return текст с раскрытыми плейсхолдерами; {@link Optional#empty()} — если
     *         в тексте остался нераскрытый плейсхолдер (кроме {@code {name}}),
     *         то есть сообщение отправлять нельзя
     */
    public static Optional<String> apply(String text, Map<String, String> values) {
        String result = text;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (result.contains(entry.getKey())) {
                result = result.replace(entry.getKey(), entry.getValue());
            }
        }
        // {name} раскрывается позже, персонально — он не считается «висящим»
        if (result.replace(NAME_PLACEHOLDER, "").contains("{")) {
            return Optional.empty();
        }
        return Optional.of(result);
    }

    // ── Наполнение снимка ─────────────────────────────────────────────────────

    /** Цена пакета знаков, количество знаков и бонус: {price_pack7}, {credits_pack7}, ... */
    private void addPackValues(Map<String, String> values, String code, String alias) {
        Optional<PaymentProduct> found = productRepository.findByCode(code)
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()));
        if (found.isEmpty()) {
            log.debug("Пакет {} недоступен — сообщения с его ценой будут пропущены", code);
            return;
        }
        PaymentProduct product = found.get();
        int credits = product.getReadingsCount() == null ? 0 : product.getReadingsCount();
        int bonus = product.getBonusCredits() == null ? 0 : product.getBonusCredits();

        values.put("{price_" + alias + "}", formatRub(product.getPriceRub()));
        values.put("{credits_" + alias + "}", String.valueOf(credits));
        values.put("{credits_" + alias + "_total}", String.valueOf(credits + bonus));
        values.put("{bonus_" + alias + "}", String.valueOf(bonus));
    }

    /** Цена плана, срок и цена за день: {price_lite}, {days_lite}, {price_lite_day} */
    private void addPlanValues(Map<String, String> values, List<SubscriptionPlan> plans,
                               String namePart, String alias) {
        Optional<SubscriptionPlan> found = plans.stream()
                .filter(p -> p.getName() != null
                        && p.getName().toLowerCase().contains(namePart.toLowerCase()))
                .findFirst();
        if (found.isEmpty()) {
            log.debug("План «{}» не найден среди активных — сообщения о нём будут пропущены", namePart);
            return;
        }
        SubscriptionPlan plan = found.get();
        int days = plan.getDurationDays() == null || plan.getDurationDays() <= 0 ? 30 : plan.getDurationDays();
        long rub = Math.round(plan.getPriceRub() / 100.0);

        values.put("{price_" + alias + "}", formatRub(plan.getPriceRub()));
        values.put("{days_" + alias + "}", String.valueOf(days));
        values.put("{price_" + alias + "_day}", String.valueOf(Math.max(1, Math.round((double) rub / days))));
    }

    /** Стоимость фич в знаках, сразу со склонением: {cost_dream_full} → «3 знака» */
    private void addCostValues(Map<String, String> values) {
        values.put("{cost_three_card_full}", creditsWithWord(featureCostService.getCost(SpreadType.THREE_CARD)));
        values.put("{cost_horseshoe_full}", creditsWithWord(featureCostService.getCost(SpreadType.HORSESHOE)));
        values.put("{cost_celtic_full}", creditsWithWord(featureCostService.getCost(SpreadType.CELTIC_CROSS)));
        values.put("{cost_dream_full}", creditsWithWord(featureCostService.getDreamCost()));
        values.put("{cost_week_full}", creditsWithWord(featureCostService.getNumerologyWeekCost()));
        values.put("{cost_month_full}", creditsWithWord(featureCostService.getNumerologyMonthCost()));
        values.put("{cost_year_full}", creditsWithWord(featureCostService.getNumerologyYearCost()));
        values.put("{cost_compat_full}", creditsWithWord(featureCostService.getCompatibilityUnlockCost()));
    }

    private boolean subscriptionsAvailable() {
        return systemConfigService.getBooleanValue(
                SystemConfigConstants.SUBSCRIPTIONS_AVAILABLE_FOR_ALL_USERS, false);
    }

    // ── Форматирование ────────────────────────────────────────────────────────

    /** Копейки → рубли без лишних нулей: 59900 → «599», 59950 → «599,50». */
    static String formatRub(int kopecks) {
        if (kopecks % 100 == 0) {
            return String.valueOf(kopecks / 100);
        }
        return String.format("%d,%02d", kopecks / 100, Math.abs(kopecks % 100));
    }

    /** «3 знака», «7 знаков», «1 знак» — чтобы фраза в тексте не рассыпалась. */
    static String creditsWithWord(int amount) {
        return amount + " " + pluralZnaki(amount);
    }

    /** Склонение слова «знак» по числу. */
    static String pluralZnaki(int amount) {
        int mod10 = amount % 10;
        int mod100 = amount % 100;
        if (mod10 == 1 && mod100 != 11) return "знак";
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) return "знака";
        return "знаков";
    }
}
