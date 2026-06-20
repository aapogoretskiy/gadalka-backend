package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.api.dto.admin.FeatureCostsDto;
import ru.sapa.gadalka_backend.domain.type.SpreadType;

import static ru.sapa.gadalka_backend.constant.SystemConfigConstants.FEATURE_COST_CELTIC_CROSS;
import static ru.sapa.gadalka_backend.constant.SystemConfigConstants.FEATURE_COST_COMPATIBILITY_UNLOCK;
import static ru.sapa.gadalka_backend.constant.SystemConfigConstants.FEATURE_COST_HORSESHOE;
import static ru.sapa.gadalka_backend.constant.SystemConfigConstants.FEATURE_COST_NUMEROLOGY_WEEK;
import static ru.sapa.gadalka_backend.constant.SystemConfigConstants.FEATURE_COST_THREE_CARD;

/**
 * Единая точка чтения стоимости платных функций (в знаках).
 *
 * <p>Значения хранятся в system_config (см. миграцию V43) и редактируются
 * через админ-панель без деплоя. Если ключ в system_config почему-то
 * отсутствует (например, миграция ещё не накатилась), используется
 * дефолт — старое захардкоженное значение — чтобы фича не "упала" на проде.
 */
@Service
@RequiredArgsConstructor
public class FeatureCostService {

    private final SystemConfigService systemConfigService;

    /** Стоимость расклада Таро по его типу. */
    public int getCost(SpreadType spreadType) {
        String key = switch (spreadType) {
            case THREE_CARD   -> FEATURE_COST_THREE_CARD;
            case HORSESHOE    -> FEATURE_COST_HORSESHOE;
            case CELTIC_CROSS -> FEATURE_COST_CELTIC_CROSS;
        };
        // Дефолт берём из самого enum — там же лежали исходные значения до выноса в конфиг
        return systemConfigService.getIntValue(key, spreadType.getCreditCost());
    }

    /** Стоимость разблокировки полного анализа совместимости. */
    public int getCompatibilityUnlockCost() {
        return systemConfigService.getIntValue(FEATURE_COST_COMPATIBILITY_UNLOCK, 3);
    }

    /** Стоимость недельного нумерологического расклада. */
    public int getNumerologyWeekCost() {
        return systemConfigService.getIntValue(FEATURE_COST_NUMEROLOGY_WEEK, 3);
    }

    /** Снимок всех текущих цен — для отображения в админ-панели. */
    public FeatureCostsDto getAllCosts() {
        return new FeatureCostsDto(
                getCost(SpreadType.THREE_CARD),
                getCost(SpreadType.HORSESHOE),
                getCost(SpreadType.CELTIC_CROSS),
                getCompatibilityUnlockCost(),
                getNumerologyWeekCost()
        );
    }

    /**
     * Обновляет все цены сразу. Каждое значение должно быть положительным —
     * иначе пользователи смогут пользоваться платной фичей бесплатно либо
     * фича станет недоступной по ошибке.
     */
    public void updateCosts(FeatureCostsDto costs) {
        if (costs.threeCard() <= 0 || costs.horseshoe() <= 0 || costs.celticCross() <= 0
                || costs.compatibilityUnlock() <= 0 || costs.numerologyWeek() <= 0) {
            throw new IllegalArgumentException("Стоимость функции должна быть положительным числом");
        }
        systemConfigService.setValue(FEATURE_COST_THREE_CARD, String.valueOf(costs.threeCard()));
        systemConfigService.setValue(FEATURE_COST_HORSESHOE, String.valueOf(costs.horseshoe()));
        systemConfigService.setValue(FEATURE_COST_CELTIC_CROSS, String.valueOf(costs.celticCross()));
        systemConfigService.setValue(FEATURE_COST_COMPATIBILITY_UNLOCK, String.valueOf(costs.compatibilityUnlock()));
        systemConfigService.setValue(FEATURE_COST_NUMEROLOGY_WEEK, String.valueOf(costs.numerologyWeek()));
    }
}
