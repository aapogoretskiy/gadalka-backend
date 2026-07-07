-- Входящие сообщения администратора внутри самого приложения (вкладка "Профиль" -> "Входящие").
--
-- Зачем: часть пользователей недостижима через Telegram (chat not found / заблокировали бота —
-- см. поле users.notifications_allowed, миграция V59). MiniApp при этом всегда доступен
-- пользователю напрямую, независимо от прав бота на проактивную отправку. Эта таблица —
-- второй, гарантированный канал доставки админских сообщений, не зависящий от Telegram API.
--
-- Без @ManyToOne — по аналогии с referral_events: это лог-таблица, простые Long-ссылки
-- на message_id/user_id проще и не тянут за собой ленивую загрузку сущностей.

CREATE TABLE inbox_messages
(
    id                            BIGSERIAL PRIMARY KEY,
    text                          TEXT        NOT NULL,
    created_at                    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_admin_telegram_id  BIGINT
);

-- read_at = NULL, пока пользователь не открыл вкладку "Входящие" (тогда помечаем прочитанными
-- разом все сообщения — см. InboxService.markAllRead).
CREATE TABLE inbox_message_recipients
(
    id         BIGSERIAL PRIMARY KEY,
    message_id BIGINT      NOT NULL REFERENCES inbox_messages (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    read_at    TIMESTAMPTZ,
    CONSTRAINT uq_inbox_recipient UNIQUE (message_id, user_id)
);

-- Основной запрос в приложении: "сколько непрочитанных у пользователя X" — покрывается индексом целиком.
CREATE INDEX idx_inbox_recipients_user_unread ON inbox_message_recipients (user_id, read_at);
-- Для агрегированной статистики в админке (сколько отправлено/прочитано по конкретному сообщению).
CREATE INDEX idx_inbox_recipients_message ON inbox_message_recipients (message_id);
