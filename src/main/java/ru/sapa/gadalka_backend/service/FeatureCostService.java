package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.api.dto.admin.FeatureCostsDto;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;
import ru.sapa.gadalka_backend.domain.type.SpreadType;

import static ru.sapa.gadalka_backend.constant.SystemConfigConstants.FEATURE_COST_CELTIC_CROSS;
import static ru.sapa.gadalka_backend.constant.SystemConfigConstants.FEATURE_COST_COMPATIBILITY_UNLOCK;
import static ru.sapa.gadalka_backend.constant.SystemConfigConstants.FEATURE_COST_DREAM;
import static ru.sapa.gadalka_backend.constant.SystemConfigConstants.FEATURE_COST_HORSESHOE;
import static ru.sapa.gadalka_backend.constant.SystemConfigConstants.FEATURE_COST_NUMEROLOGY_WEEK;
import static ru.sapa.gadalka_backend.constant.SystemConfigConstants.FEATURE_COST_NUMEROLOGY_MONTH;
import static ru.sapa.gadalka_backend.constant.SystemConfigConstants.FEATURE_COST_NUMEROLOGY_YEAR;
import static ru.sapa.gadalka_backend.constant.SystemConfigConstants.FEATURE_COST_THREE_CARD;
import static ru.sapa.gadalka_backend.domain.type.DiaryFeatureType.*;

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

    /** Стоимость месячного нумерологического разбора (4 недели внутри включены в эту цену). */
    public int getNumerologyMonthCost() {
        return systemConfigService.getIntValue(FEATURE_COST_NUMEROLOGY_MONTH, 10);
    }

    /** Стоимость годового нумерологического разбора (12 месяцев доступны бесплатно по клику). */
    public int getNumerologyYearCost() {
        return systemConfigService.getIntValue(FEATURE_COST_NUMEROLOGY_YEAR, 18);
    }

    /** Стоимость разбора сна (Сонник). */
    public int getDreamCost() {
        return systemConfigService.getIntValue(FEATURE_COST_DREAM, 3);
    }

    /** Снимок всех текущих цен — для отображения в админ-панели. */
    public FeatureCostsDto getAllCosts() {
        return new FeatureCostsDto(
                getCost(SpreadType.THREE_CARD),
                getCost(SpreadType.HORSESHOE),
                getCost(SpreadType.CELTIC_CROSS),
                getCompatibilityUnlockCost(),
                getNumerologyWeekCost(),
                getNumerologyMonthCost(),
                getNumerologyYearCost(),
                getDreamCost()
        );
    }

    /**
     * Обновляет все цены сразу. Каждое значение должно быть положительным —
     * иначе пользователи смогут пользоваться платной фичей бесплатно либо
     * фича станет недоступной по ошибке.
     */
    public void updateCosts(FeatureCostsDto costs) {
        if (costs.threeCard() <= 0 || costs.horseshoe() <= 0 || costs.celticCross() <= 0
                || costs.compatibilityUnlock() <= 0 || costs.numerologyWeek() <= 0
                || costs.numerologyMonth() <= 0 || costs.numerologyYear() <= 0 || costs.dream() <= 0) {
            throw new IllegalArgumentException("Стоимость функции должна быть положительным числом");
        }
        systemConfigService.setValue(FEATURE_COST_THREE_CARD, String.valueOf(costs.threeCard()));
        systemConfigService.setValue(FEATURE_COST_HORSESHOE, String.valueOf(costs.horseshoe()));
        systemConfigService.setValue(FEATURE_COST_CELTIC_CROSS, String.valueOf(costs.celticCross()));
        systemConfigService.setValue(FEATURE_COST_COMPATIBILITY_UNLOCK, String.valueOf(costs.compatibilityUnlock()));
        systemConfigService.setValue(FEATURE_COST_NUMEROLOGY_WEEK, String.valueOf(costs.numerologyWeek()));
        systemConfigService.setValue(FEATURE_COST_NUMEROLOGY_MONTH, String.valueOf(costs.numerologyMonth()));
        systemConfigService.setValue(FEATURE_COST_NUMEROLOGY_YEAR, String.valueOf(costs.numerologyYear()));
        systemConfigService.setValue(FEATURE_COST_DREAM, String.valueOf(costs.dream()));
    }

    /**
     * Универсальная стоимость по типу фичи — для spend-options (модалка выбора
     * способа списания) и квотных подписок. Бесплатные фичи (карта дня, гороскоп,
     * дневная нумерология, портрет) возвращают 0 — квоты/списания к ним не применяются.
     */
    public int getCost(DiaryFeatureType featureType) {
        return switch (featureType) {
            case THREE_CARD           -> getCost(SpreadType.THREE_CARD);
            case HORSESHOE            -> getCost(SpreadType.HORSESHOE);
            case CELTIC_CROSS         -> getCost(SpreadType.CELTIC_CROSS);
            case COMPATIBILITY        -> getCompatibilityUnlockCost();
            case DREAM                -> getDreamCost();
            case NUMEROLOGY_WEEK      -> getNumerologyWeekCost();
            case NUMEROLOGY_MONTH     -> getNumerologyMonthCost();
            case NUMEROLOGY_YEAR      -> getNumerologyYearCost();
            case DAILY_CARD, NUMEROLOGY_DAY, DAILY_HOROSCOPE, NUMEROLOGY_PORTRAIT -> 0;
        };
    }
}
