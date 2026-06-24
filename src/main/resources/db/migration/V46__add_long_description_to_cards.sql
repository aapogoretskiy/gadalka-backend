ALTER TABLE cards
    ADD COLUMN insight_title           VARCHAR(255),
    ADD COLUMN description_paragraph_1 TEXT,
    ADD COLUMN description_paragraph_2 TEXT,
    ADD COLUMN keywords                VARCHAR(512);
