-- Подписочная система (v1, без автопродления).
--
-- Архитектура:
--   subscription_plans       — каталог планов, редактируется из админки
--   subscription_plan_quotas — квоты плана по фичам (шаблон)
--   subscription_quotas      — СНАПШОТ квот купленной подписки + учёт расхода.
--     Снапшот нужен по той же причине, что и payments.credits_to_grant:
--     редактирование плана в админке не должно менять условия уже купленных подписок.
--
-- Квоты бывают двух видов (quota_period):
--   DAILY      — N использований в день, сбрасываются в полночь по МСК (ленивый сброс в коде)
--   PER_PERIOD — N использований на весь срок подписки, не сбрасываются

CREATE TABLE subscription_plans
(
    id            BIGSERIAL    PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,               -- отображаемое название («Базовая», «Премиум»)
    price_rub     INTEGER      NOT NULL,               -- цена в копейках (как в payment_products)
    price_stars   INTEGER      NOT NULL,               -- цена в звёздах Telegram
    duration_days INTEGER      NOT NULL DEFAULT 30,    -- срок действия подписки
    is_active     BOOLEAN      NOT NULL DEFAULT FALSE, -- неактивные не показываются в каталоге
    sort_order    INTEGER      NOT NULL DEFAULT 0,     -- порядок в каталоге (меньше = выше)
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE subscription_plan_quotas
(
    id           BIGSERIAL   PRIMARY KEY,
    plan_id      BIGINT      NOT NULL,
    feature_type VARCHAR(50) NOT NULL, -- значение enum DiaryFeatureType (THREE_CARD, DREAM, ...)
    quota_count  INTEGER     NOT NULL, -- сколько использований даёт квота
    quota_period VARCHAR(20) NOT NULL, -- DAILY | PER_PERIOD

    CONSTRAINT fk_plan_quotas_plan
        FOREIGN KEY (plan_id) REFERENCES subscription_plans (id) ON DELETE CASCADE,
    -- одна квота на фичу в рамках плана
    CONSTRAINT uq_plan_quotas_plan_feature UNIQUE (plan_id, feature_type)
);

CREATE INDEX idx_plan_quotas_plan_id ON subscription_plan_quotas (plan_id);

-- Снапшот квот конкретной купленной подписки + счётчик использования.
-- Для DAILY: used_count относится к дню usage_date (МСК); при первом обращении
-- в новый день код обнуляет used_count и сдвигает usage_date (ленивый сброс).
-- Для PER_PERIOD: usage_date не используется (NULL), used_count копится весь срок.
CREATE TABLE subscription_quotas
(
    id              BIGSERIAL   PRIMARY KEY,
    subscription_id BIGINT      NOT NULL,
    feature_type    VARCHAR(50) NOT NULL,
    quota_count     INTEGER     NOT NULL, -- снапшот из плана на момент покупки
    quota_period    VARCHAR(20) NOT NULL, -- DAILY | PER_PERIOD
    used_count      INTEGER     NOT NULL DEFAULT 0,
    usage_date      DATE,                 -- день (МСК), к которому относится used_count у DAILY-квот

    CONSTRAINT fk_subscription_quotas_subscription
        FOREIGN KEY (subscription_id) REFERENCES subscriptions (id) ON DELETE CASCADE,
    CONSTRAINT uq_subscription_quotas_sub_feature UNIQUE (subscription_id, feature_type)
);

CREATE INDEX idx_subscription_quotas_subscription_id ON subscription_quotas (subscription_id);

-- Привязка подписки к плану. plan_name — снапшот названия на момент покупки.
-- Старая колонка plan (MONTHLY/YEARLY) остаётся для совместимости, новые записи пишут туда код плана.
ALTER TABLE subscriptions
    ADD COLUMN plan_id    BIGINT REFERENCES subscription_plans (id),
    ADD COLUMN plan_name  VARCHAR(255),
    ADD COLUMN started_at TIMESTAMPTZ;

-- Платёж теперь может быть за пакет знаков ИЛИ за подписку.
-- Для подписочных платежей credits_to_grant = 0, заполняется subscription_plan_id.
ALTER TABLE payments
    ADD COLUMN purchase_type        VARCHAR(20) NOT NULL DEFAULT 'CREDITS', -- CREDITS | SUBSCRIPTION
    ADD COLUMN subscription_plan_id BIGINT REFERENCES subscription_plans (id);

-- Напоминания об истечении подписки: за 3 дня, за 2 дня и в день истечения.
-- Фиксируем последний отправленный "рубеж" (3, 2, 0), чтобы шедулер
-- не слал одно и то же напоминание дважды.
ALTER TABLE subscriptions
    ADD COLUMN last_reminder_days_left INTEGER;

-- Курс для автоподсказки цены в звёздах в админке: копеек за 1 звезду.
-- Дефолт 133 выведен из текущего каталога (199 ₽ = 150 ⭐).
INSERT INTO system_config (config_key, config_value, description)
VALUES ('STARS_RUB_RATE_KOPECKS', '133', 'Курс Telegram Stars: копеек за 1 звезду (для подсказки цены в админке)')
ON CONFLICT (config_key) DO NOTHING;
