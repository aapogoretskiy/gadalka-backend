package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.domain.FortuneCreditLogEntry;
import ru.sapa.gadalka_backend.domain.UserFortuneCredit;
import ru.sapa.gadalka_backend.domain.type.CreditTransactionReason;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;
import ru.sapa.gadalka_backend.exception.InsufficientCreditsException;
import ru.sapa.gadalka_backend.repository.FortuneCreditLogRepository;
import ru.sapa.gadalka_backend.repository.UserFortuneCreditRepository;

/**
 * Единственная точка управления балансом знаков.
 * Ниукакой другой сервис не должен напрямю трогать user_fortune_credits.
 * <p>
 * Каждое изменение баланса атомарно записывается в fortune_credit_log —
 * полный аудит-лог для поддержки и отладки.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FortuneCreditService {

    private final UserFortuneCreditRepository creditRepository;
    private final FortuneCreditLogRepository creditLogRepository;

    /**
     * Возвращает текущий баланс знаков.
     * Если записи в БД нет — возвращает 0 (новый пользователь до первого начисления).
     */
    @Transactional(readOnly = true)
    public int getBalance(Long userId) {
        return creditRepository.findByUserId(userId)
                .map(UserFortuneCredit::getBalance)
                .orElse(0);
    }

    /**
     * Проверяет, может ли пользователь использовать функцию за ЗНАКИ (баланс > 0).
     * <p>
     * С появлением квотных подписок «активная подписка» больше не означает
     * безлимитный доступ: проверка доступа по квоте живёт в
     * {@link FeatureSpendService#assertSpendable} и учитывает выбранный
     * пользователем способ оплаты (знаки или квота).
     */
    @Transactional(readOnly = true)
    public boolean canUseFeature(Long userId) {
        return getBalance(userId) > 0;
    }

    /**
     * Начисляет знаки пользователю (после успешного платежа, бонус и т.д.).
     * Операция транзакционна: баланс и лог обновляются атомарно.
     *
     * @param userId    ID пользователя
     * @param count     количество знаков для начисления
     * @param reason    причина начисления
     * @param paymentId ID платежа (null если не связано с платежом)
     */
    @Transactional
    public void grantCredits(Long userId, int count, CreditTransactionReason reason, Long paymentId) {
        if (count <= 0) throw new IllegalArgumentException("Количество начисляемых знаков должно быть > 0");

        UserFortuneCredit credit = creditRepository.findByUserId(userId)
                .orElseGet(() -> UserFortuneCredit.builder()
                        .userId(userId)
                        .balance(0)
                        .build());

        credit.setBalance(credit.getBalance() + count);
        creditRepository.save(credit);

        creditLogRepository.save(FortuneCreditLogEntry.builder()
                .userId(userId)
                .delta(count)
                .reason(reason)
                .paymentId(paymentId)
                .build());

        log.info("Начислено {} знаков: userId={}, reason={}, paymentId={}, newBalance={}",
                count, userId, reason, paymentId, credit.getBalance());
    }

    /**
     * Списывает {@code count} знаков за использование функции.
     * Использует PESSIMISTIC_WRITE lock — защита от гонки при одновременных запросах.
     * <p>
     * ВАЖНО: раньше здесь был безлимит для подписчиков (списание пропускалось).
     * С переходом на квотные подписки это убрано: оплата квотой — это явный
     * выбор пользователя, см. {@link FeatureSpendService} и {@link SubscriptionQuotaService}.
     *
     * @param userId      ID пользователя
     * @param featureType тип использованной функции
     * @param count       количество кредитов для списания (≥ 1)
     */
    @Transactional
    public void spendCredits(Long userId, DiaryFeatureType featureType, int count) {
        if (count <= 0) throw new IllegalArgumentException("Количество списываемых кредитов должно быть > 0");

        // Берём строку с блокировкой, чтобы два одновременных запроса не потратили одни кредиты
        UserFortuneCredit credit = creditRepository.findByUserIdForUpdate(userId)
                .orElseThrow(InsufficientCreditsException::new);

        if (credit.getBalance() < count) {
            log.info("Недостаточно знаков: userId={}, feature={}, нужно={}, есть={}",
                    userId, featureType, count, credit.getBalance());
            throw new InsufficientCreditsException();
        }

        credit.setBalance(credit.getBalance() - count);
        creditRepository.save(credit);

        creditLogRepository.save(FortuneCreditLogEntry.builder()
                .userId(userId)
                .delta(-count)
                .reason(CreditTransactionReason.FEATURE_SPEND)
                .featureType(featureType)
                .build());

        log.info("Списано {} знаков: userId={}, feature={}, remainingBalance={}",
                count, userId, featureType, credit.getBalance());
    }

    /**
     * Списывает кредиты за покупку темы карт.
     * Не требует DiaryFeatureType — покупка темы не является использованием функции гадания.
     * Использует защиту от гонки (PESSIMISTIC_WRITE lock).
     *
     * @param userId ID пользователя
     * @param cost   стоимость темы в кредитах
     */
    @Transactional
    public void spendCreditsForTheme(Long userId, int cost) {
        if (cost <= 0) throw new IllegalArgumentException("Стоимость темы должна быть > 0");

        UserFortuneCredit credit = creditRepository.findByUserIdForUpdate(userId)
                .orElseThrow(InsufficientCreditsException::new);

        if (credit.getBalance() < cost) {
            log.info("Недостаточно знаков для покупки темы: userId={}, нужно={}, есть={}",
                    userId, cost, credit.getBalance());
            throw new InsufficientCreditsException();
        }

        credit.setBalance(credit.getBalance() - cost);
        creditRepository.save(credit);

        creditLogRepository.save(FortuneCreditLogEntry.builder()
                .userId(userId)
                .delta(-cost)
                .reason(CreditTransactionReason.THEME_PURCHASE)
                .build());

        log.info("Списано {} знаков за покупку темы: userId={}, remainingBalance={}",
                cost, userId, credit.getBalance());
    }
}
