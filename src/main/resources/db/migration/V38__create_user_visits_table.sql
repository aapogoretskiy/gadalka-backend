-- Таблица логирования посещений пользователей.
-- Одна запись = один "сеанс" (новая активность через 5+ минут от предыдущей).
-- Используется для отчёта "повторные посещения за период".

CREATE TABLE user_visits
(
    id         BIGSERIAL   NOT NULL,
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    visited_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_user_visits PRIMARY KEY (id)
);

CREATE INDEX idx_user_visits_user_id    ON user_visits (user_id);
CREATE INDEX idx_user_visits_visited_at ON user_visits (visited_at);
