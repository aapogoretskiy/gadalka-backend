-- Поля для ретраев неуспешного автосписания (п. 6.13.1-6.13.4 пользовательского соглашения).
--
-- renewal_first_failed_at — момент ПЕРВОЙ неудачной попытки списания за текущий цикл.
-- Именно от него отсчитываются 7 календарных дней, в течение которых разрешены повторные
-- попытки (не чаще 1 раза в сутки, см. SubscriptionRenewalScheduler).
--
-- last_renewal_attempt_at — момент последней попытки списания (успешной или нет).
-- Используется для двух целей: (1) не списывать чаще раза в сутки во время ретраев,
-- (2) обнаружить платёж, зависший без вебхука дольше разумного времени
-- (см. SubscriptionRenewalScheduler#reconcileStuckRenewals).
ALTER TABLE subscriptions
    ADD COLUMN renewal_first_failed_at TIMESTAMPTZ,
    ADD COLUMN last_renewal_attempt_at TIMESTAMPTZ;
