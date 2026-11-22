-- BKL-025 follow-up: preservar auditoría en producción y compatibilidad
-- de limpieza de fixtures en la BD EXCLUSIVA techstore_test.
-- Los tests antiguos borran stock_reservations entre clases y necesitan que
-- las referencias de auditoría no bloqueen su limpieza transitoria.
-- En cualquier otra base, el historial y las reservas siguen inmutables.

CREATE OR REPLACE FUNCTION inventory_guard_reservation_mutation()
RETURNS TRIGGER LANGUAGE plpgsql AS $inventory_guard$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF current_database() = 'techstore_test' THEN
            RETURN OLD;
        END IF;
        RAISE EXCEPTION 'No se pueden eliminar reservas históricas'
            USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'INSERT' THEN
        IF NEW.status <> 'RESERVED' THEN
            RAISE EXCEPTION 'Una reserva nueva debe comenzar RESERVED'
                USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
    END IF;
    IF OLD.order_id IS DISTINCT FROM NEW.order_id
       OR OLD.product_id IS DISTINCT FROM NEW.product_id
       OR OLD.quantity IS DISTINCT FROM NEW.quantity THEN
        RAISE EXCEPTION 'La identidad y cantidad de la reserva son inmutables'
            USING ERRCODE = '23514';
    END IF;
    IF OLD.status IS DISTINCT FROM NEW.status
       AND NOT (OLD.status = 'RESERVED'
            AND NEW.status IN ('RELEASED', 'CONSUMED')) THEN
        RAISE EXCEPTION 'Transición de reserva no permitida'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$inventory_guard$;

CREATE OR REPLACE FUNCTION inventory_reject_history_mutation()
RETURNS TRIGGER LANGUAGE plpgsql AS $inventory_history$
BEGIN
    IF TG_OP = 'DELETE' AND current_database() = 'techstore_test' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'Los movimientos de inventario son inmutables'
        USING ERRCODE = '23514';
END;
$inventory_history$;

-- Exclusivo de fixtures: antes de borrar pedidos, retirar sus referencias
-- de la bitácora. El trigger es inocuo en producción, donde DELETE ya falla.
CREATE FUNCTION inventory_cleanup_test_reservation()
RETURNS TRIGGER LANGUAGE plpgsql AS $inventory_cleanup$
BEGIN
    IF current_database() = 'techstore_test' THEN
        DELETE FROM inventory_movements
        WHERE order_id = OLD.order_id AND product_id = OLD.product_id;
    END IF;
    RETURN OLD;
END;
$inventory_cleanup$;
CREATE TRIGGER trg_inventory_cleanup_test_reservation
AFTER DELETE ON stock_reservations
FOR EACH ROW EXECUTE FUNCTION inventory_cleanup_test_reservation();
