-- BKL-026: metadatos de auditoría para ajustes ADMIN realizados sobre V17.
-- No se modifican migraciones ya aplicadas ni movimientos históricos.
ALTER TABLE inventory_movements
    ADD COLUMN actor_user_id BIGINT REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN reason VARCHAR(300),
    ADD COLUMN request_id UUID;

-- Cada ajuste es atribuible, justificable e idempotente. Los eventos de
-- checkout/auditoría no deben transportar datos de ADMIN.
ALTER TABLE inventory_movements
    ADD CONSTRAINT chk_inventory_adjustment_metadata CHECK (
        (
            kind IN ('ADJUSTMENT_IN', 'ADJUSTMENT_OUT')
            AND actor_user_id IS NOT NULL
            AND request_id IS NOT NULL
            AND reason IS NOT NULL
            AND CHAR_LENGTH(reason) BETWEEN 5 AND 300
            AND reason = BTRIM(reason)
            AND reason !~ '[[:cntrl:]]'
        )
        OR
        (
            kind NOT IN ('ADJUSTMENT_IN', 'ADJUSTMENT_OUT')
            AND actor_user_id IS NULL
            AND request_id IS NULL
            AND reason IS NULL
        )
    );

-- Un mismo UUID de solicitud no puede afectar inventario dos veces.
-- El UPDATE de stock y el INSERT del movimiento corren en UNA transacción.
CREATE UNIQUE INDEX ux_inventory_adjustment_request
    ON inventory_movements (request_id) WHERE request_id IS NOT NULL;
