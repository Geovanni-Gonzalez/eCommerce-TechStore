-- BKL-020/021: consultas de catálogo indexadas sin alterar productos previos.
-- Marca opcional: los productos antiguos siguen válidos y no se inventa proveedor.
ALTER TABLE products ADD COLUMN brand VARCHAR(100);

-- FTS de nombre, descripción y SKU; case-insensitive con diccionario 'simple'.
CREATE INDEX idx_products_search_active
ON products USING GIN (to_tsvector('simple',
    coalesce(name, '') || ' ' || coalesce(description, '') || ' ' || sku))
WHERE active = TRUE;

CREATE INDEX idx_products_category_price_active
ON products (category_id, price_crc, id) WHERE active = TRUE;
CREATE INDEX idx_products_price_active
ON products (price_crc, id) WHERE active = TRUE;
CREATE INDEX idx_products_brand_active
ON products (lower(brand), id) WHERE active = TRUE;
