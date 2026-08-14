package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.sapa.gadalka_backend.bot.GadalkaTelegramBot;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.repository.UserRepository;
import ru.sapa.gadalka_backend.service.event.SubscriptionQuotasExhaustedEvent;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Сообщает пользователю, что Лимиты подписки закончились до конца оплаченного периода.
 * <p>
 * Два канала одновременно: Telegram и «Входящие» внутри приложения. Telegram пользователь
 * мог заблокировать, поэтому гарантированный канал здесь — именно «Входящие».
 * <p>
 * Работает строго после коммита транзакции, в которой был списан последний Лимит
 * ({@link TransactionPhase#AFTER_COMMIT}), и асинхронно — подробнее о причинах см.
 * {@link SubscriptionQuotasExhaustedEvent}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionQuotasExhaustedNotifier {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final UserRepository userRepository;
    private final GadalkaTelegramBot telegramBot;
    private final InboxService inboxService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQuotasExhausted(SubscriptionQuotasExhaustedEvent event) {
        inboxService.send(buildPlainText(event), List.of(event.userId()), null);

        User user = userRepository.findById(event.userId()).orElse(null);
        if (user == null || user.isBanned()) {
            return;
        }
        telegramBot.sendQuotasExhaustedNotice(user.getTelegramId(), buildMarkdownText(event));

        log.info("Уведомление об исчерпании Лимитов отправлено: userId={}, subscriptionId={}", event.userId(), event.subscriptionId());
    }

    /** Версия для «Входящих» — без Markdown, там текст показывается как есть */
    private String buildPlainText(SubscriptionQuotasExhaustedEvent event) {
        return buildText(event, "«" + planName(event) + "»");
    }

    /** Версия для Telegram — название плана выделено, как в остальных сообщениях бота */
    private String buildMarkdownText(SubscriptionQuotasExhaustedEvent event) {
        return buildText(event, "*«" + planName(event) + "»*");
    }

    private String buildText(SubscriptionQuotasExhaustedEvent event, String plan) {
        String until = event.expiresAt()
                .atZoneSameInstant(SubscriptionQuotaService.MSK)
                .format(DATE);

        String periodPart = event.autoRenewEnabled()
                ? String.format("Сама подписка действует до %s и продлится автоматически — Лимиты обновятся.", until)
                : String.format("Сама подписка действует до %s.", until);

        return String.format(
                """
                        📭 В подписке %s закончились Лимиты — всё, что в неё входило, уже потрачено.

                        %s

                        Продолжить можно прямо сейчас: оплатить разбор знаками или оформить другую подписку — она заменит текущую.""",
                plan, periodPart);
    }

    private String planName(SubscriptionQuotasExhaustedEvent event) {
        return event.planName() != null ? event.planName() : "подписка";
    }
}
