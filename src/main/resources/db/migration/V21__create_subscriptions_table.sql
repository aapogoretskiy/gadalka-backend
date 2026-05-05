-- Таблица создаётся сейчас как задел на будущее.
-- Код проверки активной подписки будет встроен в FortuneCreditService.canUseFeature()
-- с самого начала, чтобы не переписывать логику позже.
CREATE TABLE subscriptions
(
    id                       BIGSERIAL    PRIMARY KEY,
    user_id                  BIGINT       NOT NULL,
    plan                     VARCHAR(50)  NOT NULL,   -- MONTHLY, YEARLY
    status                   VARCHAR(50)  NOT NULL,   -- ACTIVE, EXPIRED, CANCELLED
    expires_at               TIMESTAMPTZ  NOT NULL,
    provider                 VARCHAR(50),
    provider_subscription_id VARCHAR(255),
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_subscriptions_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_subscriptions_user_id   ON subscriptions (user_id);
CREATE INDEX idx_subscriptions_status    ON subscriptions (status);
CREATE INDEX idx_subscriptions_expires_at ON subscriptions (expires_at);
