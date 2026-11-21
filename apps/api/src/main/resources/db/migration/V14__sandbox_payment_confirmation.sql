-- BKL-040: registrar conciliación separada de la recepción HMAC.
-- Las notificaciones SANDBOX NO son un comprobante bancario.
ALTER TABLE payment_webhook_events
    ADD COLUMN reconciliation_status VARCHAR(24) NOT NULL DEFAULT 'RECEIVED'
        CHECK (reconciliation_status IN (
            'RECEIVED', 'CONFIRMED_SIMULATED', 'DECLINED', 'NEEDS_REVIEW')),
    ADD COLUMN reconciled_at TIMESTAMPTZ;

CREATE INDEX idx_webhook_reconciliation_pending
    ON payment_webhook_events(id)
    WHERE reconciliation_status IN ('RECEIVED','NEEDS_REVIEW');

-- BKL-045: añadir motivo explícito de una confirmación SOLO simulada.
ALTER TABLE order_status_history
    DROP CONSTRAINT IF EXISTS order_status_history_reason_check;
ALTER TABLE order_status_history
    ADD CONSTRAINT chk_order_history_reason CHECK (reason IN (
        'CREATED', 'BASELINE', 'CLIENT_CANCEL', 'ADMIN_CANCEL', 'EXPIRED',
        'SANDBOX_VERIFIED'));
