-- Фиксируем момент, когда пользователь потратил гадание на полный анализ совместимости.
-- NULL = превью (бесплатно, интерпретация скрыта).
-- NOT NULL = разблокировано: timestamp момента списания.
ALTER TABLE compatibility_readings
    ADD COLUMN unlocked_at TIMESTAMPTZ NULL;
