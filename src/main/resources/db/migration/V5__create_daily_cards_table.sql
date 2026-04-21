CREATE TABLE daily_cards
(
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    card_id BIGINT NOT NULL,
    date DATE NOT NULL,

    CONSTRAINT fk_daily_cards_card
        FOREIGN KEY (card_id) REFERENCES cards(id)
);

CREATE UNIQUE INDEX uk_daily_cards_user_id_date
    ON daily_cards(user_id, date);