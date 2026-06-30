package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.domain.type.PaymentProvider;
import ru.sapa.gadalka_backend.repository.PaymentRepository;

import java.time.OffsetDateTime;

/**
 * Автоматически переводит зависшие PENDING-платежи в CANCELLED — без этого они
 * висели бы в этом статусе вечно: ни Telegram Bot API, ни (в тихом сценарии — когда
 * пользователь просто закрыл вкладку, не нажав "Отмена") Robokassa не присылают никакого
 * сигнала о том, что покупка не состоялась.
 * <p>
 * Таймаут разный по провайдерам — не потому что это "грубее" или "точнее" для какого-то
 * из них, а потому что у них объективно разное время реального подтверждения:
 * Telegram Stars подтверждаются за секунды (тап в нативном диалоге, без ввода данных),
 * Robokassa/ЮKassa — десятки секунд - минуты (ввод данных карты). См. обсуждение
 * в задаче — на проде по факту Stars подтверждались за ~2-2.5 секунды.
 * <p>
 * Эта задача — подстраховка на "тихие" случаи. Для Robokassa есть ещё и FailURL
 * (см. {@code PaymentController#robokassaFail}), который ловит явный отказ пользователя
 * почти мгновенно; для ЮKassa — вебхук {@code payment.canceled}. Здесь же — то, что
 * никаким явным сигналом не покрывается.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PendingPaymentExpiryService {

    private final PaymentRepository paymentRepository;

    @Value("${payment.pending-expiry.stars-timeout-minutes:5}")
    private int starsTimeoutMinutes;

    @Value("${payment.pending-expiry.card-timeout-minutes:30}")
    private int cardTimeoutMinutes;

    @Scheduled(fixedDelayString = "${payment.pending-expiry.check-interval-ms:300000}")
    @Transactional
    public void expireStalePendingPayments() {
        OffsetDateTime now = OffsetDateTime.now();

        int starsExpired = paymentRepository.cancelStalePendingByProvider(PaymentProvider.TELEGRAM_STARS, now.minusMinutes(starsTimeoutMinutes), now);

        int cardExpired = 0;
        for (PaymentProvider provider : new PaymentProvider[]{PaymentProvider.ROBOKASSA, PaymentProvider.YOOKASSA}) {
            cardExpired += paymentRepository.cancelStalePendingByProvider(provider, now.minusMinutes(cardTimeoutMinutes), now);
        }

        if (starsExpired > 0 || cardExpired > 0) {
            log.info("Автоотмена зависших PENDING-платежей: Stars={} (>{} мин), карточные={} (>{} мин)", starsExpired, starsTimeoutMinutes, cardExpired, cardTimeoutMinutes);
        }
    }
}
