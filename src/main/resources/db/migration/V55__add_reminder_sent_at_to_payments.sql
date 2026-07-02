-- Отметка об отправленном "догоняющем" напоминании о брошенной оплате.
-- NULL = напоминание не отправлялось. Заполняется PaymentRecoveryService
-- и служит одновременно защитой от повторной отправки по тому же платежу
-- и точкой отсчёта для анти-спам кулдауна по пользователю.
ALTER TABLE payments
    ADD COLUMN reminder_sent_at TIMESTAMPTZ;

-- Частичный индекс: PaymentRecoveryService каждые 15 минут ищет кандидатов
-- (CANCELLED/FAILED без отправленного напоминания в узком окне по created_at).
-- Полный индекс не нужен — интересны только строки с reminder_sent_at IS NULL.
CREATE INDEX idx_payments_recovery_candidates
    ON payments (created_at)
    WHERE reminder_sent_at IS NULL AND status IN ('CANCELLED', 'FAILED');
