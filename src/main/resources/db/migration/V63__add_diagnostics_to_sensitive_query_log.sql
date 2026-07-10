-- Диагностика по каждой заблокированной записи: чем поймано (keyword/LLM/бэкафилл),
-- сырой ответ LLM при провале формата (CLASSIFICATION_FAILED) и объяснение для админки.
-- Существующие записи получают источник LEGACY_UNKNOWN — они созданы до появления
-- этого поля, и мы не восстанавливаем задним числом, чем именно они были пойманы.

ALTER TABLE sensitive_query_log
    ADD COLUMN source VARCHAR(50) NOT NULL DEFAULT 'LEGACY_UNKNOWN',
    ADD COLUMN raw_classification_output TEXT,
    ADD COLUMN explanation TEXT;

ALTER TABLE sensitive_query_log
    ALTER COLUMN source DROP DEFAULT;

CREATE INDEX idx_sensitive_query_log_source ON sensitive_query_log (source);
