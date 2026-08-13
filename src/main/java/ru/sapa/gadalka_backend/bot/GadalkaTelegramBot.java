package ru.sapa.gadalka_backend.bot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerPreCheckoutQuery;
import org.telegram.telegrambots.meta.api.methods.menubutton.SetChatMenuButton;
import org.telegram.telegrambots.meta.api.methods.payments.RefundStarPayment;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.menubutton.MenuButtonWebApp;
import org.telegram.telegrambots.meta.api.objects.payments.PreCheckoutQuery;
import org.telegram.telegrambots.meta.api.objects.payments.SuccessfulPayment;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.sapa.gadalka_backend.repository.UserRepository;
import ru.sapa.gadalka_backend.service.PaymentService;
import ru.sapa.gadalka_backend.service.ReferralService;
import ru.sapa.gadalka_backend.service.stars.TelegramStarsService;

import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "telegram.bot.enabled", havingValue = "true")
public class GadalkaTelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final TelegramClient telegramClient;
    private final ReferralService referralService;
    private final PaymentService paymentService;
    private final TelegramStarsService starsService;
    private final UserRepository userRepository;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.app-url}")
    private String appUrl;

    public GadalkaTelegramBot(TelegramClient telegramClient,
                              ReferralService referralService,
                              PaymentService paymentService,
                              TelegramStarsService starsService,
                              UserRepository userRepository) {
        this.telegramClient = telegramClient;
        this.referralService = referralService;
        this.paymentService = paymentService;
        this.starsService = starsService;
        this.userRepository = userRepository;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        if (update.hasMessage()) {
            markNotificationsAllowed(update.getMessage().getChatId(), update.getMessage().hasWriteAccessAllowed());
        }

        // Обычные текстовые сообщения
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            if (text.equals("/start") || text.startsWith("/start ")) {
                String referralCode = extractReferralCode(text);
                if (referralCode != null) {
                    referralService.recordBotEntry(chatId, referralCode);
                }
                setChatMenuButton(chatId);
                sendWelcomeMessage(chatId, referralCode);
            }
            return;
        }

        // PreCheckoutQuery — Telegram спрашивает "можно ли провести платёж?"
        // Мы ОБЯЗАНЫ ответить в течение 10 секунд, иначе платёж будет отклонён.
        if (update.hasPreCheckoutQuery()) {
            handlePreCheckoutQuery(update.getPreCheckoutQuery());
            return;
        }

        // SuccessfulPayment — платёж Stars прошёл успешно
        if (update.hasMessage() && update.getMessage().hasSuccessfulPayment()) {
            handleSuccessfulPayment(update.getMessage().getSuccessfulPayment());
        }
    }

    /**
     * Помечает пользователя как достижимого для проактивных сообщений бота.
     * <p>
     * Вызывается на любое входящее сообщение — включая write_access_allowed,
     * которое Telegram присылает после {@code WebApp.requestWriteAccess()} в Mini App.
     * Если пользователя ещё нет в БД (например, самое первое /start до открытия
     * Mini App) — просто ничего не делаем, флаг проставится позже при авторизации
     * и первой успешной отправке.
     */
    private void markNotificationsAllowed(long chatId, boolean viaWriteAccessRequest) {
        userRepository.findByTelegramId(chatId).ifPresent(user -> {
            if (user.isNotificationsAllowed()) return;
            user.setNotificationsAllowed(true);
            userRepository.save(user);
            log.info("notificationsAllowed=true для telegramId={} ({})",
                    chatId, viaWriteAccessRequest ? "write_access_allowed" : "входящее сообщение");
        });
    }

    private void setChatMenuButton(long chatId) {
        try {
            MenuButtonWebApp menuButton = MenuButtonWebApp.builder()
                    .text("🔮 Открыть")
                    .webAppInfo(new WebAppInfo(appUrl))
                    .build();

            telegramClient.execute(SetChatMenuButton.builder()
                    .chatId(chatId)
                    .menuButton(menuButton)
                    .build());

            log.info("Menu button установлен для chatId={}", chatId);
        } catch (TelegramApiException e) {
            // Не критично — приложение продолжит работу, просто кнопки не будет
            log.warn("Не удалось установить menu button для chatId={}: {}", chatId, e.getMessage());
        }
    }

    /**
     * Обрабатывает запрос на предварительную проверку перед оплатой Stars.
     * Telegram ждёт ответа максимум 10 секунд — отвечаем быстро.
     * В нашем случае всегда разрешаем — реальная проверка происходит после SuccessfulPayment.
     */
    private void handlePreCheckoutQuery(PreCheckoutQuery query) {
        log.info("PreCheckoutQuery: id={}, userId={}, payload={}",
                query.getId(), query.getFrom().getId(), query.getInvoicePayload());
        try {
            telegramClient.execute(AnswerPreCheckoutQuery.builder()
                    .preCheckoutQueryId(query.getId())
                    .ok(true)
                    .build());
        } catch (TelegramApiException e) {
            log.error("Ошибка ответа на PreCheckoutQuery id={}: {}", query.getId(), e.getMessage(), e);
        }
    }

    /**
     * Обрабатывает успешный Stars-платёж.
     * telegramPaymentChargeId — уникальный ID транзакции Telegram (идемпотентность).
     * invoicePayload — наш внутренний Payment.id, который мы передали при создании инвойса.
     */
    private void handleSuccessfulPayment(SuccessfulPayment successfulPayment) {
        Long internalPaymentId = starsService.extractInternalPaymentId(successfulPayment);
        String chargeId = starsService.extractTelegramChargeId(successfulPayment);

        log.info("SuccessfulPayment Stars: internalPaymentId={}, chargeId={}", internalPaymentId, chargeId);

        try {
            paymentService.processStarsSuccess(internalPaymentId, chargeId);
        } catch (Exception e) {
            // Логируем, но не падаем — деньги уже списаны, нужно разобраться вручную
            log.error("Ошибка обработки Stars платежа: internalPaymentId={}, chargeId={}, error={}",
                    internalPaymentId, chargeId, e.getMessage(), e);
        }
    }

    /**
     * Отправляет пользователю уведомление о подарке от администратора.
     * Вызывается из AdminController при начислении кредитов через админ-панель.
     *
     * @param telegramId Telegram ID получателя
     * @param amount     количество подаренных знаков
     */
    public void sendGiftNotification(Long telegramId, int amount) {
        String text = "🎁 *Вам подарок от команды Гадалки!*\n\n" +
                "На ваш счёт зачислено *" + amount + " " + pluralZnaki(amount) + "*.\n\n" +
                "Откройте приложение и используйте их для новых гаданий ✨";

        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text("🔮 Открыть Гадалку")
                .webApp(new WebAppInfo(appUrl))
                .build();

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboard(List.of(new InlineKeyboardRow(button)))
                .build();

        SendMessage message = SendMessage.builder()
                .chatId(telegramId)
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(keyboard)
                .build();

        try {
            telegramClient.execute(message);
            log.info("Уведомление о подарке отправлено: telegramId={}, amount={}", telegramId, amount);
        } catch (TelegramApiException e) {
            // Не критично для операции начисления — кредиты уже зачислены.
            // Пользователь может не принять сообщения от бота (заблокировал бота).
            log.warn("Не удалось отправить уведомление о подарке: telegramId={}, error={}", telegramId, e.getMessage());
        }
    }

    /**
     * Возврат платежа Telegram Stars через Bot API. Вызывается из
     * {@link ru.sapa.gadalka_backend.service.SubscriptionCancellationService}
     * при оформлении возврата админом.
     *
     * @param telegramId       Telegram ID пользователя-плательщика
     * @param telegramChargeId telegram_payment_charge_id успешного платежа
     * @return true — Telegram подтвердил возврат
     */
    public boolean refundStarPayment(Long telegramId, String telegramChargeId) {
        try {
            var refund = RefundStarPayment.builder()
                    .userId(telegramId)
                    .telegramPaymentChargeId(telegramChargeId)
                    .build();
            telegramClient.execute(refund);
            log.info("Stars возвращены: telegramId={}, chargeId={}", telegramId, telegramChargeId);
            return true;
        } catch (TelegramApiException e) {
            log.error("Не удалось вернуть Stars: telegramId={}, chargeId={}, error={}", telegramId, telegramChargeId, e.getMessage());
            return false;
        }
    }

    /**
     * Уведомление о подаренных админом квотах — аналог {@link #sendGiftNotification},
     * но для квот подписки. Вызывается из AdminController#giftQuota.
     *
     * @param telegramId Telegram ID получателя
     * @param quotaText  готовое описание подарка, например «3 × Разбор сна (в день)»
     */
    public void sendQuotaGiftNotification(Long telegramId, String quotaText) {
        String text = "🎁 *Вам подарок от команды Гадалки!*\n\n" +
                "Вам выданы квоты: *" + quotaText + "*.\n\n" +
                "Откройте приложение и используйте их для новых предсказаний ✨";

        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text("🔮 Открыть Гадалку")
                .webApp(new WebAppInfo(appUrl))
                .build();

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboard(List.of(new InlineKeyboardRow(button)))
                .build();

        SendMessage message = SendMessage.builder()
                .chatId(telegramId)
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(keyboard)
                .build();

        try {
            telegramClient.execute(message);
            log.info("Уведомление о подарке-квоте отправлено: telegramId={}", telegramId);
        } catch (TelegramApiException e) {
            // Не критично — квоты уже выданы, пользователь мог заблокировать бота
            log.warn("Не удалось отправить уведомление о подарке-квоте: telegramId={}, error={}",
                    telegramId, e.getMessage());
        }
    }

    /**
     * Отправляет напоминание о брошенной оплате с кнопкой, открывающей Mini App
     * сразу на экране оплаты. Вызывается из {@link ru.sapa.gadalka_backend.service.PaymentRecoveryService}.
     * <p>
     * Кнопка ведёт на {@code appUrl?screen=pay} — фронтенд при инициализации читает
     * query-параметр {@code screen} и роутит пользователя на PaymentScreen (см. App.vue).
     *
     * @param telegramId Telegram ID получателя
     * @param text       готовый текст напоминания (Markdown)
     * @return true — сообщение отправлено; false — Telegram отклонил отправку
     *         (например, пользователь заблокировал бота)
     */
    public boolean sendPaymentRecoveryMessage(Long telegramId, String text) {
        String payUrl = appUrl + (appUrl.contains("?") ? "&" : "?") + "screen=pay";

        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text("💳 Оплатить картой")
                .webApp(new WebAppInfo(payUrl))
                .build();

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboard(List.of(new InlineKeyboardRow(button)))
                .build();

        SendMessage message = SendMessage.builder()
                .chatId(telegramId)
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(keyboard)
                .build();

        try {
            telegramClient.execute(message);
            log.info("Напоминание о брошенной оплате отправлено: telegramId={}", telegramId);
            return true;
        } catch (TelegramApiException e) {
            // Не критично: пользователь мог заблокировать бота. Платёж всё равно
            // помечается как "напоминание отправлено", чтобы не долбить повторно.
            log.warn("Не удалось отправить напоминание об оплате: telegramId={}, error={}",
                    telegramId, e.getMessage());
            return false;
        }
    }

    /**
     * Напоминание об истечении подписки (за 3/2/0 дней до конца).
     * Кнопка ведёт на экран оплаты — там пользователь может продлить подписку.
     * Вызывается из {@link ru.sapa.gadalka_backend.service.SubscriptionReminderScheduler}.
     *
     * @return true — отправлено; false — Telegram отклонил (например, бот заблокирован)
     */
    public boolean sendSubscriptionExpiryReminder(Long telegramId, String text) {
        String payUrl = appUrl + (appUrl.contains("?") ? "&" : "?") + "screen=pay";

        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text("🔮 Продлить подписку")
                .webApp(new WebAppInfo(payUrl))
                .build();

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboard(List.of(new InlineKeyboardRow(button)))
                .build();

        SendMessage message = SendMessage.builder()
                .chatId(telegramId)
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(keyboard)
                .build();

        try {
            telegramClient.execute(message);
            log.info("Напоминание об истечении подписки отправлено: telegramId={}", telegramId);
            return true;
        } catch (TelegramApiException e) {
            log.warn("Не удалось отправить напоминание о подписке: telegramId={}, error={}",
                    telegramId, e.getMessage());
            return false;
        }
    }

    /**
     * Обязательное по п. 6.12.4 соглашения уведомление не позднее чем за 3 календарных дня
     * до автосписания за продление подписки. Кнопка ведёт на экран профиля — там статус
     * подписки, отключение автопродления и отмена подписки (см. ProfileScreen.vue).
     * Вызывается из {@link ru.sapa.gadalka_backend.service.SubscriptionRenewalScheduler}.
     *
     * @return true — отправлено; false — Telegram отклонил (например, бот заблокирован)
     */
    public boolean sendAutoRenewNotice(Long telegramId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(telegramId)
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(profileKeyboard("⚙️ Открыть профиль"))
                .build();

        try {
            telegramClient.execute(message);
            log.info("Уведомление об автосписании отправлено: telegramId={}", telegramId);
            return true;
        } catch (TelegramApiException e) {
            log.warn("Не удалось отправить уведомление об автосписании: telegramId={}, error={}",
                    telegramId, e.getMessage());
            return false;
        }
    }

    /**
     * Сообщает пользователю, что списание за продление не удалось и доступ к Лимитам подписки
     * временно приостановлен — но автопродление не выключено, будем повторять попытку
     * автоматически до {@code retryDeadline} включительно (п. 6.13.1-6.13.2 соглашения).
     * Отправляется один раз, при первой неудачной попытке за цикл — повторные неудачи
     * в течение окна ретраев молча логируются, не спамим пользователя каждый день.
     * Вызывается из {@link ru.sapa.gadalka_backend.service.SubscriptionRenewalScheduler}.
     */
    public void sendAutoRenewSuspendedNotice(Long telegramId, String planName, OffsetDateTime retryDeadline) {
        String name = planName != null ? planName : "подписка";
        String deadlineText = retryDeadline.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

        SendMessage message = SendMessage.builder()
                .chatId(telegramId)
                .text(String.format(
                        """
                                ⏸ Не получилось списать оплату за подписку *«%s»* — доступ к лимитам подписки временно приостановлен.

                                Мы будем автоматически повторять попытку до %s. Если она пройдёт успешно — доступ восстановится сам, ничего делать не нужно.

                                Проверить статус или отключить автопродление можно в разделе «Профиль».""",
                        name, deadlineText))
                .parseMode("Markdown")
                .replyMarkup(profileKeyboard("⚙️ Открыть профиль"))
                .build();

        try {
            telegramClient.execute(message);
            log.info("Уведомление о приостановке подписки отправлено: telegramId={}", telegramId);
        } catch (TelegramApiException e) {
            log.warn("Не удалось отправить уведомление о приостановке подписки: telegramId={}, error={}",
                    telegramId, e.getMessage());
        }
    }

    /**
     * Сообщает пользователю, что все попытки автосписания за отведённые 7 дней не увенчались
     * успехом — подписка завершена, автопродление выключено (п. 6.13.4 соглашения). Долга
     * за неоплаченный период не возникает, поэтому текст не пугает пользователя задолженностью.
     * Вызывается из {@link ru.sapa.gadalka_backend.service.SubscriptionRenewalScheduler}.
     */
    public void sendAutoRenewTerminatedNotice(Long telegramId, String planName) {
        String payUrl = appUrl + (appUrl.contains("?") ? "&" : "?") + "screen=pay";
        String name = planName != null ? planName : "подписка";

        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text("💳 Оформить подписку")
                .webApp(new WebAppInfo(payUrl))
                .build();

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboard(List.of(new InlineKeyboardRow(button)))
                .build();

        SendMessage message = SendMessage.builder()
                .chatId(telegramId)
                .text(String.format(
                        "❌ Не получилось продлить подписку *«%s»* — попытки списания за 7 дней не увенчались успехом. " +
                        "Подписка завершена, автопродление выключено.\n\n" +
                        "Оформите подписку заново в любое время.", name))
                .parseMode("Markdown")
                .replyMarkup(keyboard)
                .build();

        try {
            telegramClient.execute(message);
            log.info("Уведомление о завершении подписки отправлено: telegramId={}", telegramId);
        } catch (TelegramApiException e) {
            log.warn("Не удалось отправить уведомление о завершении подписки: telegramId={}, error={}",
                    telegramId, e.getMessage());
        }
    }

    /**
     * Сообщает активным автопродлеваемым подписчикам плана, что его цена изменилась —
     * но их собственная цена уже зафиксирована и меняться не будет (п. 6.11.3(1), 6.14
     * соглашения). Вызывается из {@link ru.sapa.gadalka_backend.service.SubscriptionPlanAdminService}.
     */
    public boolean sendPlanPriceChangedNotice(Long telegramId, String planName, int lockedPriceRubKopecks, int newPriceRubKopecks) {
        SendMessage message = SendMessage.builder()
                .chatId(telegramId)
                .text(String.format(
                        """
                                ℹ️ Мы обновили цену тарифа *«%s»*: теперь %s ₽ за расчётный период.

                                У вас уже подключено автопродление — ваша цена зафиксирована и остаётся прежней: %s ₽. Менять ничего не нужно.

                                Если захотите перейти на новую цену — оформите подписку заново в разделе «Профиль».""",
                        planName, formatRub(newPriceRubKopecks), formatRub(lockedPriceRubKopecks)))
                .parseMode("Markdown")
                .replyMarkup(profileKeyboard("⚙️ Открыть профиль"))
                .build();

        try {
            telegramClient.execute(message);
            log.info("Уведомление об изменении цены плана отправлено: telegramId={}", telegramId);
            return true;
        } catch (TelegramApiException e) {
            log.warn("Не удалось отправить уведомление об изменении цены плана: telegramId={}, error={}",
                    telegramId, e.getMessage());
            return false;
        }
    }

    /** Кнопка, открывающая Mini App сразу на экране профиля (управление подпиской). */
    /**
     * Сообщает, что подписка закрыта досрочно — все включённые в неё разборы потрачены
     * (см. {@code SubscriptionExhaustedNotifier}). Текст формирует вызывающая сторона:
     * то же самое сообщение уходит и во «Входящие» внутри приложения, только без Markdown.
     */
    public void sendSubscriptionExhaustedNotice(Long telegramId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(telegramId)
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(profileKeyboard("⚙️ Открыть профиль"))
                .build();

        try {
            telegramClient.execute(message);
            log.info("Уведомление об исчерпанной подписке отправлено: telegramId={}", telegramId);
        } catch (TelegramApiException e) {
            log.warn("Не удалось отправить уведомление об исчерпанной подписке: telegramId={}, error={}",
                    telegramId, e.getMessage());
        }
    }

    private InlineKeyboardMarkup profileKeyboard(String buttonText) {
        String profileUrl = appUrl + (appUrl.contains("?") ? "&" : "?") + "screen=profile";
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text(buttonText)
                .webApp(new WebAppInfo(profileUrl))
                .build();
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(new InlineKeyboardRow(button)))
                .build();
    }

    /** Форматирует копейки в рубли для текста уведомлений («299», а не «299.0»). */
    private String formatRub(int kopecks) {
        double rub = kopecks / 100.0;
        return rub == Math.floor(rub) ? String.valueOf((long) rub) : String.format("%.2f", rub);
    }

    /**
     * Отправляет рефереру уведомление о том, что его друг зарегистрировался.
     * Вызывается из {@link ru.sapa.gadalka_backend.service.ReferralService}.
     *
     * @param referrerTelegramId Telegram ID реферера
     * @param newUserName        имя нового пользователя
     * @param rewardCredits      количество начисленных знаков
     */
    public void sendReferralRewardNotification(Long referrerTelegramId, String newUserName, int rewardCredits) {
        String text = "🎉 *Ваш друг присоединился к Гадалке!*\n\n" +
                "*" + escapeMarkdown(newUserName) + "* зарегистрировался по вашей реферальной ссылке.\n\n" +
                "В благодарность мы зачислили вам *" + rewardCredits + " " + pluralZnaki(rewardCredits) + "* ✨\n\n" +
                "Продолжайте приглашать друзей — за каждого нового пользователя вы получите " +
                rewardCredits + " " + pluralZnaki(rewardCredits) + " 🔮";

        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text("🔮 Открыть Гадалку")
                .webApp(new WebAppInfo(appUrl))
                .build();

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboard(List.of(new InlineKeyboardRow(button)))
                .build();

        SendMessage message = SendMessage.builder()
                .chatId(referrerTelegramId)
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(keyboard)
                .build();

        try {
            telegramClient.execute(message);
            log.info("Реферальное уведомление отправлено: referrerTelegramId={}, newUser={}, credits={}",
                    referrerTelegramId, newUserName, rewardCredits);
        } catch (TelegramApiException e) {
            // Не критично — кредиты уже зачислены
            log.warn("Не удалось отправить реферальное уведомление: telegramId={}, error={}",
                    referrerTelegramId, e.getMessage());
        }
    }

    /** Экранирует спецсимволы Markdown для безопасной вставки в текст */
    private String escapeMarkdown(String text) {
        return text.replace("_", "\\_").replace("*", "\\*").replace("`", "\\`").replace("[", "\\[");
    }

    /**
     * Отправляет произвольное сообщение пользователю в рамках массовой рассылки.
     * Если {@code giftAmount} передан — добавляет в конец сообщения информацию о начисленных знаках.
     * Кнопку "Открыть" всегда прикрепляем, чтобы конверсия в возврат была выше.
     *
     * <p>Метод бросает исключение при ошибке — вызывающий {@link ru.sapa.gadalka_backend.service.BroadcastService}
     * перехватит его и посчитает как failed-отправку.
     *
     * @param telegramId Telegram ID получателя
     * @param text       текст сообщения (поддерживает Markdown)
     * @param giftAmount количество начисленных знаков (null — не упоминать)
     */
    public void sendBroadcastMessage(Long telegramId, String text, Integer giftAmount) {
        String fullText = text;
        if (giftAmount != null && giftAmount > 0) {
            fullText += "\n\n🎁 На ваш счёт зачислено *" + giftAmount + " " + pluralZnaki(giftAmount) + "*";
        }

        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text("🔮 Открыть Гадалку")
                .webApp(new WebAppInfo(appUrl))
                .build();

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboard(List.of(new InlineKeyboardRow(button)))
                .build();

        SendMessage message = SendMessage.builder()
                .chatId(telegramId)
                .text(fullText)
                .parseMode("Markdown")
                .replyMarkup(keyboard)
                .build();

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            // Бросаем дальше — BroadcastService обработает и посчитает как ошибку
            throw new RuntimeException("Telegram API error for telegramId=" + telegramId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Первая отправка фото в рамках рассылки: загружает байты на серверы Telegram,
     * получает из ответа file_id и возвращает его для переиспользования.
     *
     * <p>Telegram сохраняет файл у себя, и все последующие отправки можно делать
     * через {@link #sendPhotoBroadcastMessage(Long, String, String, Integer)},
     * передавая полученный file_id — без повторной загрузки байтов.
     *
     * @param telegramId  Telegram ID первого получателя
     * @param photoBytes  байты изображения
     * @param fileName    имя файла (нужно Telegram для определения MIME-типа)
     * @param caption     подпись под фото (поддерживает Markdown)
     * @param giftAmount  количество начисленных знаков (null — не упоминать)
     * @return file_id сохранённого на серверах Telegram файла
     */
    public String sendPhotoBroadcastMessageUpload(Long telegramId,
                                                  byte[] photoBytes,
                                                  String fileName,
                                                  String caption,
                                                  Integer giftAmount) {
        String fullCaption = buildCaption(caption, giftAmount);

        InlineKeyboardMarkup keyboard = buildOpenAppKeyboard();

        SendPhoto sendPhoto = SendPhoto.builder()
                .chatId(telegramId)
                .photo(new InputFile(new ByteArrayInputStream(photoBytes), fileName != null ? fileName : "photo.jpg"))
                .caption(fullCaption)
                .parseMode("Markdown")
                .replyMarkup(keyboard)
                .build();

        try {
            var response = telegramClient.execute(sendPhoto);
            // Берём самый большой вариант фото — у него самый стабильный file_id
            var photos = response.getPhoto();
            return photos.get(photos.size() - 1).getFileId();
        } catch (TelegramApiException e) {
            throw new RuntimeException("Telegram API photo upload error for telegramId=" + telegramId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Повторная отправка фото в рамках рассылки: использует file_id,
     * полученный при первой отправке через {@link #sendPhotoBroadcastMessageUpload}.
     * Telegram берёт файл со своих серверов — байты по сети не передаются.
     *
     * @param telegramId Telegram ID получателя
     * @param fileId     file_id, ранее полученный от Telegram
     * @param caption    подпись под фото (поддерживает Markdown)
     * @param giftAmount количество начисленных знаков (null — не упоминать)
     */
    public void sendPhotoBroadcastMessage(Long telegramId,
                                          String fileId,
                                          String caption,
                                          Integer giftAmount) {
        String fullCaption = buildCaption(caption, giftAmount);

        SendPhoto sendPhoto = SendPhoto.builder()
                .chatId(telegramId)
                .photo(new InputFile(fileId))
                .caption(fullCaption)
                .parseMode("Markdown")
                .replyMarkup(buildOpenAppKeyboard())
                .build();

        try {
            telegramClient.execute(sendPhoto);
        } catch (TelegramApiException e) {
            throw new RuntimeException("Telegram API photo error for telegramId=" + telegramId + ": " + e.getMessage(), e);
        }
    }

    /** Строит подпись: основной текст + опциональная строка о подарке */
    private String buildCaption(String caption, Integer giftAmount) {
        if (giftAmount != null && giftAmount > 0) {
            return caption + "\n\n🎁 На ваш счёт зачислено *" + giftAmount + " " + pluralZnaki(giftAmount) + "*";
        }
        return caption;
    }

    /** Клавиатура с кнопкой "Открыть Гадалку" — используется во всех рассылочных сообщениях */
    private InlineKeyboardMarkup buildOpenAppKeyboard() {
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text("🔮 Открыть Гадалку")
                .webApp(new WebAppInfo(appUrl))
                .build();
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(new InlineKeyboardRow(button)))
                .build();
    }

    /**
     * Отправляет уведомление пользователю при закрытии заявки обратной связи с подарком знаков.
     *
     * @param telegramId Telegram ID получателя
     * @param amount     количество подаренных знаков
     */
    public void sendSupportClosedWithGift(Long telegramId, int amount) {
        String text = "🙏 *Спасибо, что не равнодушны!*\n\n" +
                "Мы рассмотрели ваше обращение и приносим извинения за доставленные неудобства.\n\n" +
                "В знак благодарности дарим вам *" + amount + " " + pluralZnaki(amount) + "* ✨\n\n" +
                "Откройте приложение и используйте их для новых гаданий 🔮";

        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text("🔮 Открыть Гадалку")
                .webApp(new WebAppInfo(appUrl))
                .build();

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboard(List.of(new InlineKeyboardRow(button)))
                .build();

        SendMessage message = SendMessage.builder()
                .chatId(telegramId)
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(keyboard)
                .build();

        try {
            telegramClient.execute(message);
            log.info("Уведомление о закрытии заявки с подарком отправлено: telegramId={}, amount={}", telegramId, amount);
        } catch (TelegramApiException e) {
            // Не критично — кредиты уже зачислены, заявка закрыта
            log.warn("Не удалось отправить уведомление о закрытии заявки: telegramId={}, error={}", telegramId, e.getMessage());
        }
    }

    /** Склонение слова "знак" по количеству */
    private String pluralZnaki(int amount) {
        int mod10 = amount % 10;
        int mod100 = amount % 100;
        if (mod10 == 1 && mod100 != 11) return "знак";
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) return "знака";
        return "знаков";
    }

    /**
     * Извлекает реферальный код из команды вида "/start telegram_channel1".
     * Возвращает null, если код отсутствует или пустой.
     * Метод публичный для удобства тестирования.
     */
    public String extractReferralCode(String startCommand) {
        if (startCommand == null || !startCommand.startsWith("/start ")) return null;
        String code = startCommand.substring("/start ".length()).trim();
        return code.isEmpty() ? null : code;
    }

    private void sendWelcomeMessage(long chatId, String referralCode) {
        // Чтобы Mini App получает start_param в initData при открытии
        String webAppUrl = (referralCode != null && !referralCode.isBlank())
                ? appUrl + "?startapp=" + referralCode
                : appUrl;

        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text("🔮 Открыть Гадалку")
                .webApp(new WebAppInfo(webAppUrl))
                .build();

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboard(List.of(new InlineKeyboardRow(button)))
                .build();

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("""
                        ✨ *Добро пожаловать в Гадалку!*
                        
                        Здесь карты Таро раскроют тайны вашего прошлого, настоящего и будущего.
                        
                        Нажмите кнопку ниже, чтобы открыть приложение и получить свой персональный расклад 🌙""")
                .parseMode("Markdown")
                .replyMarkup(keyboard)
                .build();

        try {
            telegramClient.execute(message);
            log.info("Приветственное сообщение отправлено: chatId={}, referralCode={}", chatId, referralCode);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки приветственного сообщения, chatId={}: {}", chatId, e.getMessage(), e);
        }
    }
}
