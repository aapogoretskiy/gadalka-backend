-- Счётчик суммарных действий пользователя (гадания + совместимость + нумерология + карта дня).
-- Инкрементируется в сервисах при создании новой записи (не при отдаче из кэша).
ALTER TABLE users ADD COLUMN total_actions_count INT NOT NULL DEFAULT 0;

-- Заполняем исторические данные: считаем уже существующие записи по всем 4 таблицам
UPDATE users u SET total_actions_count = (SELECT COUNT(*) FROM fortunes WHERE user_id = u.id)
                              +
                          (SELECT COUNT(*) FROM compatibility_readings WHERE user_id = u.id)
                              +
                          (SELECT COUNT(*) FROM numerology_day_readings WHERE user_id = u.id)
                              +
                          (SELECT COUNT(*) FROM daily_cards WHERE user_id = u.id);
