-- Разметка платежей для рекуррентности.
--
-- auto_renew_requested — согласился ли пользователь на автопродление в момент
-- оформления ЭТОГО платежа (чекбокс на экране оплаты), либо это платёж, который
-- сами создали как автоматическое продление (SubscriptionRenewalScheduler).
-- Нужно на Payment, а не только на Subscription, потому что в момент клика по
-- чекбоксу подписка ещё не существует — она появится только после webhook
-- (см. SubscriptionActivationService.activateFromPayment).
--
-- renewal_of_subscription_id — если этот платёж — автоматическое рекуррентное
-- списание, здесь id той Subscription, которую мы продлеваем. Нужно, чтобы
-- activateFromPayment мог унаследовать в новую строку Subscription правильный
-- root_payment_id (тот, что был у продлеваемой подписки), а не взять id
-- текущего платежа — иначе цепочка PreviousInvoiceID у Robokassa рассыпется.
-- NULL — обычная (первая/ручная) покупка, не автосписание.
ALTER TABLE payments
    ADD COLUMN auto_renew_requested       BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN renewal_of_subscription_id BIGINT;
