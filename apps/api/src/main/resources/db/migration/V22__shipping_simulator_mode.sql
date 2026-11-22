-- BKL-052: modo ficticio y opt-in; migración incremental sobre V20/V21.
-- No se reescriben registros MANUAL previos. La dirección sigue en orders.
ALTER TABLE shipments DROP CONSTRAINT chk_shipments_method;
ALTER TABLE shipments ADD CONSTRAINT chk_shipments_method
    CHECK (fulfillment_method IN ('MANUAL','SIMULATOR'));

-- Diferenciar el origen del evento desde la restricción y desde el payload.
ALTER TABLE shipment_status_history DROP CONSTRAINT chk_shipment_history_steps;
ALTER TABLE shipment_status_history ADD CONSTRAINT chk_shipment_history_steps CHECK (
    (from_status IS NULL AND to_status='PENDING_MANUAL'
        AND reason IN ('MANUAL_REGISTERED','SIMULATOR_REGISTERED'))
    OR (from_status='PENDING_MANUAL' AND to_status='IN_TRANSIT'
        AND reason IN ('MANUAL_DISPATCH','SIMULATOR_DISPATCH'))
    OR (from_status='IN_TRANSIT' AND to_status='DELIVERED'
        AND reason IN ('MANUAL_DELIVERY','SIMULATOR_DELIVERY'))
);

-- El trigger existente de auditoría se reemplaza sin modificar V21:
-- un registro SIMULATOR queda identificado en los tres hitos.
CREATE OR REPLACE FUNCTION shipping_audit_manual_status() RETURNS TRIGGER
LANGUAGE plpgsql AS $shipping_audit$
DECLARE
    simulation BOOLEAN;
BEGIN
    simulation := NEW.fulfillment_method = 'SIMULATOR';
    IF TG_OP='INSERT' THEN
        INSERT INTO shipment_status_history
            (shipment_id,from_status,to_status,actor_user_id,reason,changed_at)
        VALUES (NEW.id,NULL,'PENDING_MANUAL',NEW.opened_by_user_id,
                CASE WHEN simulation
                    THEN 'SIMULATOR_REGISTERED' ELSE 'MANUAL_REGISTERED' END,
                NEW.registered_at);
    ELSIF OLD.status IS DISTINCT FROM NEW.status THEN
        INSERT INTO shipment_status_history
            (shipment_id,from_status,to_status,actor_user_id,reason,changed_at)
        VALUES (NEW.id,OLD.status,NEW.status,NEW.last_changed_by_user_id,
                CASE WHEN simulation THEN
                    CASE NEW.status
                        WHEN 'IN_TRANSIT' THEN 'SIMULATOR_DISPATCH'
                        WHEN 'DELIVERED' THEN 'SIMULATOR_DELIVERY'
                    END
                ELSE
                    CASE NEW.status
                        WHEN 'IN_TRANSIT' THEN 'MANUAL_DISPATCH'
                        WHEN 'DELIVERED' THEN 'MANUAL_DELIVERY'
                    END
                END,
                NEW.status_updated_at);
    END IF;
    RETURN NEW;
END;
$shipping_audit$;

-- Los triggers de V21 ya registran auditoría inmutable y restringen los saltos.
-- La clave UNIQUE(order_id) de V20 sigue impidiendo mezclar proveedores.
