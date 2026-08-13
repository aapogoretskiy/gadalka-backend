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
import ru.sapa.gadalka_backend.service.event.SubscriptionExhaustedEvent;

import java.util.List;

/**
 * Сообщает пользователю, что его подписка закрыта досрочно — все включённые в неё
 * разборы потрачены, а автопродление (если было) отключено.
 * <p>
 * Два канала одновременно: Telegram и «Входящие» внутри приложения. Telegram может быть
 * заблокирован пользователем, поэтому гарантированный канал здесь — именно «Входящие»:
 * сообщение о том, что автосписаний больше не будет, человек должен получить в любом случае.
 * <p>
 * Работает строго после коммита транзакции, в которой была списана последняя квота
 * ({@link TransactionPhase#AFTER_COMMIT}), и асинхронно — подробнее о причинах см.
 * {@link SubscriptionExhaustedEvent}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionExhaustedNotifier {

    private final UserRepository userRepository;
    private final GadalkaTelegramBot telegramBot;
    private final InboxService inboxService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubscriptionExhausted(SubscriptionExhaustedEvent event) {
        inboxService.send(buildPlainText(event), List.of(event.userId()), null);

        User user = userRepository.findById(event.userId()).orElse(null);
        if (user == null || user.isBanned()) {
            return;
        }
        telegramBot.sendSubscriptionExhaustedNotice(user.getTelegramId(), buildMarkdownText(event));

        log.info("Уведомление об исчерпанной подписке отправлено: userId={}, subscriptionId={}",
                event.userId(), event.subscriptionId());
    }

    /** Версия для «Входящих» — без Markdown, там текст показывается как есть */
    private String buildPlainText(SubscriptionExhaustedEvent event) {
        return buildText(event, "«" + planName(event) + "»");
    }

    /** Версия для Telegram — название плана выделено, как в остальных сообщениях бота */
    private String buildMarkdownText(SubscriptionExhaustedEvent event) {
        return buildText(event, "*«" + planName(event) + "»*");
    }

    private String buildText(SubscriptionExhaustedEvent event, String plan) {
        String autoRenewPart = event.autoRenewWasEnabled()
                ? "Автопродление отключено — новых списаний не будет."
                : "Автопродление у неё не было включено, списаний не будет.";

        return String.format(
                """
                        🔮 Подписка %s полностью использована — всё, что в неё входило, потрачено.

                        %s

                        Оформить новую подписку можно в разделе «Профиль».""",
                plan, autoRenewPart);
    }

    private String planName(SubscriptionExhaustedEvent event) {
        return event.planName() != null ? event.planName() : "подписка";
    }
}
