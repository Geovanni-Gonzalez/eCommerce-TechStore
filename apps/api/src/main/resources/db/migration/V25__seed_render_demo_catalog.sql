-- Catalogo exclusivo para la demostracion de Render TechStore.
-- Nunca siembra productos en DBs locales, CI ni entornos de produccion diferentes.
-- Basado en scripts/dev/seed-catalog.sql: solo datos ficticios, no usuarios ni pedidos.
-- Las operaciones usan ON CONFLICT DO NOTHING para no alterar un producto existente.
-- La migracion Flyway se ejecuta una vez; el trigger de V17 audita stock inicial.
INSERT INTO categories (name)
SELECT 'Accesorios Demo'
WHERE current_database() = 'techstore_demo'
ON CONFLICT (name) DO NOTHING;

INSERT INTO products (
    category_id, sku, name, description, price_crc, stock, active, brand
)
SELECT c.id, d.sku, d.name, d.description,
       d.price_crc, d.stock, d.active, d.brand
FROM categories c
CROSS JOIN (
    VALUES
      ('DEMO-MOUSE-01', 'Mouse inalámbrico Demo',
       'Mouse de ejemplo para TechStore', 12990.50::numeric, 15, TRUE, 'TechDemo'),
      ('DEMO-KEY-01', 'Teclado mecánico Demo',
       'Teclado de ejemplo para TechStore', 35990.00::numeric, 8, TRUE, 'KeyDemo'),
      ('DEMO-HIDDEN-01', 'Producto oculto Demo',
       'Ejemplo de producto inactivo', 19990.00::numeric, 3, FALSE, 'HiddenDemo')
) AS d(sku, name, description, price_crc, stock, active, brand)
WHERE current_database() = 'techstore_demo'
  AND c.name = 'Accesorios Demo'
ON CONFLICT (sku) DO NOTHING;
