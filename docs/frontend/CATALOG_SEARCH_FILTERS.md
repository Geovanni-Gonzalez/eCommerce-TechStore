# BKL-020/021/022 — Búsqueda, filtros y UI del catálogo

## Búsqueda y filtros sobre la API real

- GET `/api/products?page=0&size=12&q=mouse&categoryId=1&brand=TechDemo&minPriceCrc=1000&maxPriceCrc=50000`
- GET `/api/products/filters` devuelve categorías y marcas que están presentes en productos activos.
- La búsqueda utiliza PostgreSQL Full Text Search de nombre, descripción y SKU en español tal cual (configuración `simple`), sin diferencias de mayúsculas; busca términos/palabras, no subcadenas arbitrarias.
- Categoría se filtra por ID; marca por igualdad insensible a mayúsculas; precio por límites inclusivos CRC.
- Todas las condiciones son opcionales y combinables. La consulta SQL es parametrizada y devuelve solo activos, con `ORDER BY id` y paginación determinista.
- Flyway V16 agrega `brand VARCHAR(100)` nullable, índices parciales de categoría/precio/marca y GIN de búsqueda. Los productos anteriores conservan `brand = NULL`, sin inventar proveedor.
- El seed de demostración añade marcas ficticias a mouse y teclado (solo entorno local/test).

## Interfaz `/productos`

- Formulario con término, categoría, marca, precio mínimo y máximo.
- Parámetros compartibles en URL y paginación que conserva la combinación aplicada.
- Estados vacío, error y carga; CTA para limpiar filtros; foco visible y diseño móvil.
- El catálogo de productos, carrito y checkout siguen funcionando sin filtros; la paginación por defecto es 12.
- Marca y categoría se muestran cuando el backend aporta datos; no se rellena información falsa.

## Limitaciones / fase final de validación

- BKL-020 utiliza búsqueda de palabras completas con PostgreSQL FTS, no un buscador externo ni autocompletado semántico.
- No se valida que una marca introducida exista: un valor desconocido produce cero resultados.
- La imagen sigue siendo opcional hasta incorporarse un origen con licencia apropiada.
- Conforme a la decisión del equipo, las pruebas exploratorias, rendimiento, compatibilidad móvil y seguridad se concentrarán al finalizar el producto. No se han ejecutado pruebas manuales de esta nueva rama.
