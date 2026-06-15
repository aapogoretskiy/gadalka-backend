-- Фича-тогл персонализированной рассылки.
-- Когда значение 'true' — в тексте рассылки плейсхолдер {name} заменяется
-- на имя пользователя (User.firstName). При 'false' — текст отправляется как есть.

INSERT INTO system_config (config_key, config_value, description, created_at, updated_at)
VALUES ('BROADCAST_PERSONALIZATION_ENABLED',
        'false',
        'Включить замену {name} на имя пользователя в массовой рассылке',
        NOW(),
        NOW());
