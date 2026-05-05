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
import ru.sapa.gadalka_backend.repository.SubscriptionRepository;
import ru.sapa.gadalka_backend.repository.UserFortuneCreditRepository;

import java.time.OffsetDateTime;

/**
 * Единственная точка управления балансом гаданий.
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
    private final SubscriptionRepository subscriptionRepository;

    /**
     * Возвращает текущий баланс гаданий.
     * Если записи в БД нет — возвращает 0 (новый пользователь до первого начисления).
     */
    @Transactional(readOnly = true)
    public int getBalance(Long userId) {
        return creditRepository.findByUserId(userId)
                .map(UserFortuneCredit::getBalance)
                .orElse(0);
    }

    /**
     * Проверяет, может ли пользователь использовать функцию.
     * Доступ есть если: баланс > 0 ИЛИ есть активная подписка.
     * <p>
     * Метод уже готов для будущей подписочной логики — достаточно будет
     * добавить записи в subscriptions, и проверка заработает автоматически.
     */
    @Transactional(readOnly = true)
    public boolean canUseFeature(Long userId) {
        if (getBalance(userId) > 0) return true;

        // Проверяем активную подписку (сейчас таблица всегда пуста)
        return subscriptionRepository
                .findActiveByUserId(userId, OffsetDateTime.now())
                .isPresent();
    }

    /**
     * Начисляет гадания пользователю (после успешного платежа, бонус и т.д.).
     * Операция транзакционна: баланс и лог обновляются атомарно.
     *
     * @param userId    ID пользователя
     * @param count     количество гаданий для начисления
     * @param reason    причина начисления
     * @param paymentId ID платежа (null если не связано с платежом)
     */
    @Transactional
    public void grantCredits(Long userId, int count, CreditTransactionReason reason, Long paymentId) {
        if (count <= 0) throw new IllegalArgumentException("Количество начисляемых гаданий должно быть > 0");

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

        log.info("Начислено {} гаданий: userId={}, reason={}, paymentId={}, newBalance={}",
                count, userId, reason, paymentId, credit.getBalance());
    }

    /**
     * Списывает 1 гадание за использование функции.
     * Использует PESSIMISTIC_WRITE lock — защита от гонки при одновременных запросах.
     * <p>
     * Если у пользователя нет активной подписки и баланс = 0 → кидает InsufficientCreditsException.
     * Вызывается ДО выполнения основного действия (AI-запроса и т.д.),
     * чтобы при нехватке кредитов не тратить ресурсы.
     *
     * @param userId      ID пользователя
     * @param featureType тип использованной функции
     */
    @Transactional
    public void spendCredit(Long userId, DiaryFeatureType featureType) {
        // Пользователи с активной подпиской не тратят кредиты
        boolean hasSubscription = subscriptionRepository
                .findActiveByUserId(userId, OffsetDateTime.now())
                .isPresent();

        if (hasSubscription) {
            log.debug("Пользователь с подпиской использует функцию: userId={}, feature={}", userId, featureType);
            return;
        }

        // Берём строку с блокировкой, чтобы два одновременных запроса не потратили один кредит
        UserFortuneCredit credit = creditRepository.findByUserIdForUpdate(userId)
                .orElseThrow(InsufficientCreditsException::new);

        if (credit.getBalance() <= 0) {
            log.info("Недостаточно гаданий: userId={}, feature={}", userId, featureType);
            throw new InsufficientCreditsException();
        }

        credit.setBalance(credit.getBalance() - 1);
        creditRepository.save(credit);

        creditLogRepository.save(FortuneCreditLogEntry.builder()
                .userId(userId)
                .delta(-1)
                .reason(CreditTransactionReason.FEATURE_SPEND)
                .featureType(featureType)
                .build());

        log.info("Списано 1 гадание: userId={}, feature={}, remainingBalance={}",
                userId, featureType, credit.getBalance());
    }
}
