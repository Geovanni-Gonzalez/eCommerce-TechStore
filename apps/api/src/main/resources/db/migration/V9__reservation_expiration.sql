-- BKL-027: vencimiento de reservas PENDING; conservar V1-V8.
ALTER TABLE stock_reservations ADD COLUMN expires_at TIMESTAMPTZ;
UPDATE stock_reservations SET expires_at = reserved_at + INTERVAL '30 minutes';
ALTER TABLE stock_reservations
    ALTER COLUMN expires_at SET NOT NULL,
    ALTER COLUMN expires_at SET DEFAULT (CURRENT_TIMESTAMP + INTERVAL '30 minutes');
CREATE INDEX idx_stock_reservations_expiry ON stock_reservations (expires_at, order_id)
    WHERE status = 'RESERVED';
