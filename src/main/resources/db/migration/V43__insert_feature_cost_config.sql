-- Стоимость платных функций в знаках (кредитах).
-- Раньше значения были захардкожены в Java (SpreadType, CompatibilityService,
-- NumerologyWeekService). Теперь они читаются из system_config, чтобы менять
-- цены можно было через админ-панель без деплоя.

INSERT INTO system_config (config_key, config_value, description, created_at, updated_at)
VALUES
    ('FEATURE_COST_THREE_CARD',
     '3',
     'Стоимость расклада "Три карты" в знаках',
     NOW(),
     NOW()),
    ('FEATURE_COST_HORSESHOE',
     '6',
     'Стоимость расклада "Подкова" в знаках',
     NOW(),
     NOW()),
    ('FEATURE_COST_CELTIC_CROSS',
     '9',
     'Стоимость расклада "Кельтский крест" в знаках',
     NOW(),
     NOW()),
    ('FEATURE_COST_COMPATIBILITY_UNLOCK',
     '3',
     'Стоимость полного отчёта по совместимости в знаках',
     NOW(),
     NOW()),
    ('FEATURE_COST_NUMEROLOGY_WEEK',
     '3',
     'Стоимость расклада на неделю (нумерология) в знаках',
     NOW(),
     NOW());
