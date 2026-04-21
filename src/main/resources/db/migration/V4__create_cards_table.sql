CREATE TABLE cards
(
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    arcana_type VARCHAR(20) NOT NULL, -- MAJOR / MINOR
    suit VARCHAR(20),                 -- WANDS / CUPS / SWORDS / PENTACLES
    rank VARCHAR(20),                 -- ACE / TWO / ... / KING
    meaning TEXT NOT NULL,
    advice TEXT,
    image_url VARCHAR(1024)
);