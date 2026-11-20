-- BKL-033: reservas respaldadas por decremento atómico de products.stock.
-- Se crean con la orden en la misma transacción. No modificar V1-V6.
CREATE TABLE stock_reservations (
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 99),
    status VARCHAR(12) NOT NULL DEFAULT 'RESERVED'
        CHECK (status IN ('RESERVED', 'RELEASED', 'CONSUMED')),
    reserved_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_stock_reservation PRIMARY KEY (order_id, product_id)
);

CREATE INDEX idx_stock_reservations_product_status
    ON stock_reservations (product_id, status);
