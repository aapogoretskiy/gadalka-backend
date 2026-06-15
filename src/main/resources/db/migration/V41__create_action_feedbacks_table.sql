-- Фидбэк пользователя на платные действия (гадания, совместимость).
-- Архитектура расширяема: action_type — строковый enum, новые типы добавляются
-- без изменения схемы (только новое значение в FeedbackTargetType).
CREATE TABLE action_feedbacks (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    action_type VARCHAR(50)  NOT NULL,   -- FORTUNE, COMPATIBILITY (расширяемо)
    action_id   BIGINT       NOT NULL,   -- id записи в соответствующей таблице
    rating      VARCHAR(10)  NOT NULL,   -- POSITIVE, NEGATIVE
    comment     TEXT,                    -- опциональный текст при NEGATIVE
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- один фидбэк на одно действие от одного пользователя
    CONSTRAINT uq_action_feedback UNIQUE (user_id, action_type, action_id)
);

CREATE INDEX idx_action_feedbacks_action ON action_feedbacks (action_type, action_id);
