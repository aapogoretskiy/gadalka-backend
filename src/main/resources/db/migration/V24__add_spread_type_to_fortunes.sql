-- Добавляем тип расклада к существующим гаданиям.
-- Nullable: старые записи (THREE_CARD) не имеют этого поля,
-- приложение будет возвращать THREE_CARD по умолчанию при null.
ALTER TABLE fortunes
    ADD COLUMN spread_type VARCHAR(20);

-- Ретроактивно помечаем все старые записи как THREE_CARD
UPDATE fortunes
SET spread_type = 'THREE_CARD'
WHERE spread_type IS NULL;

COMMENT ON COLUMN fortunes.spread_type IS 'Тип расклада: THREE_CARD, HORSESHOE, CELTIC_CROSS';
