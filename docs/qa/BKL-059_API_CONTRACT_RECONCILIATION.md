# BKL-059 — Reconciliación del contrato de reseñas

## Defecto confirmado y corregido

Tras BKL-051, `ProductReviewController` implementó
`GET /api/reviews/products/{productId}` para devolver hasta 50 reseñas
activas, pero `docs/api/openapi.json` documentaba únicamente el
`POST` de la misma ruta. El contrato público estaba incompleto.

Este cambio añade la operación `listActiveProductReviews` como pública
(`security: []`), devuelve un arreglo de `PublicProductReview`
`{rating, comment, createdAt}`, limita 50 elementos y no incorpora
usuario, pedido ni datos personales. Los endpoints privados de BKL-050
continúan exigiendo Bearer CLIENT.

El validador estructural registra la nueva operación y protege, mediante
pruebas negativas, que el GET no adquiera accidentalmente Bearer ni un DTO
con `userId`. Se mantiene la actualización del endpoint de avisos in-app
de BKL-053: total esperado **54 operaciones** tras ambas integraciones.

## Dependencia y límites

Este PR se basa sobre la rama BKL-053, no sobre el `dev` actual.
Fusionar primero BKL-053 y reconciliar la base de este PR antes del merge.
El cambio corrige un **desfase específico** del contrato; todavía no
certifica ausencia de todos los defectos críticos. BKL-059 completo requiere
evaluación de BKL-055/BKL-056, CI en verde y revisión cruzada.
