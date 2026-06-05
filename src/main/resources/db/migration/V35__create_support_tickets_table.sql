CREATE TABLE support_tickets (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users(id),
    description TEXT          NOT NULL,
    status      VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    closed_at   TIMESTAMPTZ,
    credits_gifted INTEGER
);

CREATE INDEX idx_support_tickets_user_id ON support_tickets(user_id);
CREATE INDEX idx_support_tickets_status  ON support_tickets(status);
