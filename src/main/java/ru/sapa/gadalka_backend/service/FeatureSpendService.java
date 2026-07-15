package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;
import ru.sapa.gadalka_backend.domain.type.SpendMode;
import ru.sapa.gadalka_backend.exception.InsufficientCreditsException;
import ru.sapa.gadalka_backend.exception.QuotaExceededException;

/**
 * Единая точка оплаты платных действий: знаками или квотой подписки.
 * Бизнес-сервисы (гадания, сонник, совместимость, нумерология) не знают
 * деталей — они просто вызывают {@link #assertSpendable} до дорогого AI-запроса
 * и {@link #spend} после успешной генерации.
 * <p>
 * Семантика квоты: 1 использование фичи = 1 единица квоты, независимо от
 * стоимости фичи в знаках (кельтский крест за 5 знаков — это одна квота).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureSpendService {

    private final FortuneCreditService fortuneCreditService;
    private final SubscriptionQuotaService subscriptionQuotaService;

    /**
     * Предварительная проверка ДО выполнения дорогого действия (AI-запроса):
     * при нехватке знаков/квоты падаем сразу, не тратя токены.
     * <p>
     * Проверка не резервирует средства — фактическое списание в {@link #spend}
     * защищено pessimistic lock'ом. Небольшое окно между проверкой и списанием
     * допустимо: в худшем случае spend() кинет то же исключение после генерации.
     */
    public void assertSpendable(Long userId, DiaryFeatureType featureType, int creditCost, SpendMode mode) {
        if (mode == SpendMode.QUOTA) {
            SubscriptionQuotaService.QuotaState state = subscriptionQuotaService
                    .getQuotaState(userId, featureType)
                    .orElseThrow(QuotaExceededException::noQuotaForFeature);
            if (state.remaining() <= 0) throw QuotaExceededException.quotaExhausted();
        } else {
            if (fortuneCreditService.getBalance(userId) < creditCost) {
                throw new InsufficientCreditsException();
            }
        }
    }

    /**
     * Списывает оплату за успешно выполненное действие.
     *
     * @param creditCost стоимость в знаках (используется только при mode = CREDITS)
     */
    public void spend(Long userId, DiaryFeatureType featureType, int creditCost, SpendMode mode) {
        if (mode == SpendMode.QUOTA) {
            subscriptionQuotaService.spendQuota(userId, featureType);
            return;
        }
        fortuneCreditService.spendCredits(userId, featureType, creditCost);
    }
}
