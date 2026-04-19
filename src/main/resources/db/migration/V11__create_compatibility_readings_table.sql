CREATE TABLE compatibility_readings
(
    id              BIGSERIAL    PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    persons_hash    VARCHAR(64)  NOT NULL,
    persons         TEXT         NOT NULL,
    score           INT     NOT NULL,
    label           VARCHAR(100) NOT NULL,
    categories      TEXT         NOT NULL,
    interpretation  TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_compatibility_readings_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE UNIQUE INDEX uk_compatibility_readings_user_id_persons_hash
    ON compatibility_readings (user_id, persons_hash);
