-- Обновляем пакет PACK_15:
-- цена 349₽ → 359₽ (35900 копеек), 260 → 270 Stars (сохраняем курс ~1.33₽/звезда),
-- бонус +2 → +3 знака в подарок (покупатель получает 15 + 3 = 18 знаков).
UPDATE payment_products
SET price_rub     = 35900,
    price_stars   = 270,
    bonus_credits = 3
WHERE code = 'PACK_15';
