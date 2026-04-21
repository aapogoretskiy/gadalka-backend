ALTER TABLE cards
    ADD CONSTRAINT unique_card UNIQUE (arcana_type, suit, rank);