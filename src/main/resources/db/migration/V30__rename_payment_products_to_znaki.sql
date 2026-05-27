-- Переименовываем пакеты в каталоге платежей:
-- "гадания" → "знаки" в соответствии с новым названием внутренней валюты.
UPDATE payment_products SET name = '3 знака'   WHERE code = 'PACK_3';
UPDATE payment_products SET name = '7 знаков'  WHERE code = 'PACK_7';
UPDATE payment_products SET name = '15 знаков' WHERE code = 'PACK_15';
