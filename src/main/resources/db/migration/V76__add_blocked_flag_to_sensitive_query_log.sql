ALTER TABLE sensitive_query_log ADD COLUMN blocked BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE sensitive_query_log SET blocked = FALSE WHERE source IN ('BACKFILL_KEYWORD', 'BACKFILL_LLM');
