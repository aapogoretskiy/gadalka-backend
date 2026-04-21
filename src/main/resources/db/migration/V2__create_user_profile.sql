CREATE TABLE user_profile
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT UNIQUE NOT NULL,
    birth_date DATE          NOT NULL,
    birth_time TIME,
    birth_city VARCHAR(255)  NOT NULL,
    CONSTRAINT fk_user_profile_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
);