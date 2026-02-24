CREATE TABLE users
(
    id          BIGSERIAL PRIMARY KEY,
    telegram_id BIGINT    NOT NULL UNIQUE,
    username    VARCHAR(255),
    first_name  VARCHAR(255),
    last_name   VARCHAR(255),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);