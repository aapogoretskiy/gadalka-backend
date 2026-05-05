CREATE TABLE payment_products
(
    id             BIGSERIAL    PRIMARY KEY,
    code           VARCHAR(50)  NOT NULL UNIQUE,
    name           VARCHAR(255) NOT NULL,
    readings_count INTEGER      NOT NULL,
    price_rub      INTEGER      NOT NULL, -- в копейках (9900 = 99₽)
    price_stars    INTEGER      NOT NULL, -- в звёздах Telegram
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order     INTEGER      NOT NULL DEFAULT 0
);

INSERT INTO payment_products (code, name, readings_count, price_rub, price_stars, sort_order)
VALUES ('PACK_3',  '3 гадания',  3,  9900,  75,  1),
       ('PACK_7',  '7 гаданий',  7,  19900, 150, 2),
       ('PACK_15', '15 гаданий', 15, 34900, 260, 3);
