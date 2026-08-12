package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.api.dto.admin.FeatureBadgeDto;
import ru.sapa.gadalka_backend.api.dto.admin.FeatureBadgesDto;

import java.time.LocalDateTime;

import static ru.sapa.gadalka_backend.constant.SystemConfigConstants.*;

/**
 * Отметки «Новинка» / «Хит» на платных функциях — управляются из админки
 * (вкладка «Цены»), хранятся в system_config по тому же принципу, что и
 * сама стоимость функций (см. {@link FeatureCostService}).
 *
 * <p>Раньше бейджи «Новинка»/«Хит» были захардкожены прямо в вёрстке экранов —
 * это неудобно, потому что для показа/снятия отметки требовался деплой фронтенда.
 * Теперь админ включает/выключает их без деплоя, а точки-уведомления в навигации
 * и рамки на карточках подтягивают актуальное состояние с бэкенда.
 */
@Service
@RequiredArgsConstructor
public class FeatureBadgeService {

    private final SystemConfigService systemConfigService;

    /** Снимок всех текущих отметок — для отображения в админ-панели и в самом приложении. */
    public FeatureBadgesDto getAllBadges() {
        return new FeatureBadgesDto(
                readBadge(FEATURE_NEW_THREE_CARD, FEATURE_HOT_THREE_CARD),
                readBadge(FEATURE_NEW_HORSESHOE, FEATURE_HOT_HORSESHOE),
                readBadge(FEATURE_NEW_CELTIC_CROSS, FEATURE_HOT_CELTIC_CROSS),
                readBadge(FEATURE_NEW_COMPATIBILITY_UNLOCK, FEATURE_HOT_COMPATIBILITY_UNLOCK),
                readBadge(FEATURE_NEW_NUMEROLOGY_WEEK, FEATURE_HOT_NUMEROLOGY_WEEK),
                readBadge(FEATURE_NEW_NUMEROLOGY_MONTH, FEATURE_HOT_NUMEROLOGY_MONTH),
                readBadge(FEATURE_NEW_NUMEROLOGY_YEAR, FEATURE_HOT_NUMEROLOGY_YEAR),
                readBadge(FEATURE_NEW_DREAM, FEATURE_HOT_DREAM),
                readBadge(FEATURE_NEW_SUBSCRIPTIONS, FEATURE_HOT_SUBSCRIPTIONS)
        );
    }

    private FeatureBadgeDto readBadge(String newKey, String hotKey) {
        boolean isNew = systemConfigService.getBooleanValue(newKey, false);
        boolean isHot = systemConfigService.getBooleanValue(hotKey, false);
        LocalDateTime newSince = isNew ? systemConfigService.getUpdatedAt(newKey) : null;
        return new FeatureBadgeDto(isNew, isHot, newSince);
    }

    /**
     * Обновляет все отметки сразу.
     *
     * <p>Важно: пишем в system_config только те ключи, значение которых реально
     * поменялось. Если писать всё подряд при каждом сохранении формы цен (даже
     * когда чекбоксы не трогали), у записи будет каждый раз обновляться updated_at —
     * а значит и {@code newSince}, который фронтенд использует, чтобы понять,
     * видел ли пользователь эту «Новинку» уже после того, как её включили.
     * Ненужные перезаписи будут заставлять точку-уведомление появляться заново
     * без причины.
     */
    public void updateBadges(FeatureBadgesDto badges) {
        writeBadge(FEATURE_NEW_THREE_CARD, FEATURE_HOT_THREE_CARD, badges.threeCard());
        writeBadge(FEATURE_NEW_HORSESHOE, FEATURE_HOT_HORSESHOE, badges.horseshoe());
        writeBadge(FEATURE_NEW_CELTIC_CROSS, FEATURE_HOT_CELTIC_CROSS, badges.celticCross());
        writeBadge(FEATURE_NEW_COMPATIBILITY_UNLOCK, FEATURE_HOT_COMPATIBILITY_UNLOCK, badges.compatibilityUnlock());
        writeBadge(FEATURE_NEW_NUMEROLOGY_WEEK, FEATURE_HOT_NUMEROLOGY_WEEK, badges.numerologyWeek());
        writeBadge(FEATURE_NEW_NUMEROLOGY_MONTH, FEATURE_HOT_NUMEROLOGY_MONTH, badges.numerologyMonth());
        writeBadge(FEATURE_NEW_NUMEROLOGY_YEAR, FEATURE_HOT_NUMEROLOGY_YEAR, badges.numerologyYear());
        writeBadge(FEATURE_NEW_DREAM, FEATURE_HOT_DREAM, badges.dream());
        writeBadge(FEATURE_NEW_SUBSCRIPTIONS, FEATURE_HOT_SUBSCRIPTIONS, badges.subscriptions());
    }

    private void writeBadge(String newKey, String hotKey, FeatureBadgeDto value) {
        if (value == null) {
            return;
        }
        writeIfChanged(newKey, value.isNew());
        writeIfChanged(hotKey, value.isHot());
    }

    private void writeIfChanged(String key, boolean value) {
        boolean current = systemConfigService.getBooleanValue(key, false);
        if (current != value) {
            systemConfigService.setValue(key, String.valueOf(value));
        }
    }
}
