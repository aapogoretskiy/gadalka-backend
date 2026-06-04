-- Добавляем колонку bonus_credits в payment_products.
-- Позволяет задавать бонусные знаки сверх основного пакета (например, +2 к PACK_15).
-- DEFAULT 0 — существующие пакеты без бонуса, не требуют отдельного UPDATE.
ALTER TABLE payment_products
    ADD COLUMN bonus_credits INTEGER NOT NULL DEFAULT 0;

-- PACK_15: покупатель получает 15 + 2 = 17 знаков
UPDATE payment_products SET bonus_credits = 2 WHERE code = 'PACK_15';
