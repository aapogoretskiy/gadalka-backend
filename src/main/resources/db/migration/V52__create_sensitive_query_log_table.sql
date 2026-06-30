CREATE TABLE sensitive_query_log
(
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    question    TEXT         NOT NULL,
    category    VARCHAR(100) NOT NULL,
    detected_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_sensitive_query_log_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_sensitive_query_log_user_id     ON sensitive_query_log (user_id);
CREATE INDEX idx_sensitive_query_log_category    ON sensitive_query_log (category);
CREATE INDEX idx_sensitive_query_log_detected_at ON sensitive_query_log (detected_at);
