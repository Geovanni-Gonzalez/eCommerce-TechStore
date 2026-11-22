-- DATOS EXCLUSIVOS DE DEMOSTRACIÓN; ejecutar manualmente en desarrollo.
-- NO es una migración Flyway: no inserta datos en producción automáticamente.
-- Idempotente por category.name y products.sku.
\set ON_ERROR_STOP on
BEGIN;

INSERT INTO categories (name)
VALUES ('Accesorios Demo')
ON CONFLICT (name) DO NOTHING;

INSERT INTO products (category_id, sku, name, description, price_crc, stock, active, brand)
VALUES
  ((SELECT id FROM categories WHERE name = 'Accesorios Demo'),
   'DEMO-MOUSE-01', 'Mouse inalámbrico Demo', 'Mouse de ejemplo para TechStore', 12990.50, 15, TRUE, 'TechDemo'),
  ((SELECT id FROM categories WHERE name = 'Accesorios Demo'),
   'DEMO-KEY-01', 'Teclado mecánico Demo', 'Teclado de ejemplo para TechStore', 35990.00, 8, TRUE, 'KeyDemo'),
  ((SELECT id FROM categories WHERE name = 'Accesorios Demo'),
   'DEMO-HIDDEN-01', 'Producto oculto Demo', 'Ejemplo de producto inactivo', 19990.00, 3, FALSE, 'HiddenDemo')
ON CONFLICT (sku) DO UPDATE SET brand = COALESCE(products.brand, EXCLUDED.brand);

COMMIT;
