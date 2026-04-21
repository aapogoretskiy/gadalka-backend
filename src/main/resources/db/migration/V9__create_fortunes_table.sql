CREATE TABLE fortunes
(
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT        NOT NULL,
    question_hash    VARCHAR(64)   NOT NULL,
    question         TEXT          NOT NULL,
    cards            TEXT          NOT NULL,
    interpretation   TEXT          NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_fortunes_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE UNIQUE INDEX uk_fortunes_user_id_question_hash
    ON fortunes (user_id, question_hash);
