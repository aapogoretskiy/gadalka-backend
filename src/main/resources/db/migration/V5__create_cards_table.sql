CREATE TABLE cards
(
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    meaning TEXT NOT NULL,
    advice TEXT,
    image_url VARCHAR(1024)
);