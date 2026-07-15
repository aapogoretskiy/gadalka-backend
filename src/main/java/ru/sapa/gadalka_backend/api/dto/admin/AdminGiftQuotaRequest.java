package ru.sapa.gadalka_backend.api.dto.admin;

import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;
import ru.sapa.gadalka_backend.domain.type.QuotaPeriod;

/**
 * Запрос на выдачу квоты пользователю из админ-панели
 * (POST /api/admin/users/{id}/gift-quota).
 *
 * @param featureType  фича, на которую выдаётся квота
 * @param count        сколько использований добавить (> 0)
 * @param quotaPeriod  периодичность новой квоты (у уже существующей не меняется)
 * @param durationDays срок подарочной подписки в днях — используется только если
 *                     активной подписки нет и создаётся подарочная (дефолт 30)
 */
public record AdminGiftQuotaRequest(
        DiaryFeatureType featureType,
        Integer count,
        QuotaPeriod quotaPeriod,
        Integer durationDays
) {
}
