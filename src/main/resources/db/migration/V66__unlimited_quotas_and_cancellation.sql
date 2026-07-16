-- Безлимитные квоты, отказ от подписки и возвраты.
--
-- «Безлимит» (Superb): маркетинговый безлимит со скрытым дневным анти-абьюз
-- лимитом (quota_count, по умолчанию 15/день). Пользователь видит «Безлимит»,
-- числа не раскрываются. Технически это DAILY-квота с флагом is_unlimited.
--
-- Отказ от подписки: пользователь добровольно освобождает слот «одной подписки»
-- (квоты и срок сгорают, без автовозврата). cancelled_at — момент отказа.
--
-- Возврат: админ оформляет через админку, payments.status = REFUNDED,
-- refunded_at — момент оформления возврата.

ALTER TABLE subscription_plan_quotas
    ADD COLUMN is_unlimited BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE subscription_quotas
    ADD COLUMN is_unlimited BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE subscriptions
    ADD COLUMN cancelled_at TIMESTAMPTZ;

ALTER TABLE payments
    ADD COLUMN refunded_at TIMESTAMPTZ;
