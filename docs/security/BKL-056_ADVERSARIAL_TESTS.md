# BKL-056 — Pruebas adversariales de autenticación, IDOR y webhooks

## Cobertura automatizada

- `OrderQueryHttpIT` (BKL-042, extendido por BKL-049) comprueba 401 sin
  Bearer, 403 por rol incorrecto, 404 indistinguible en pedido ajeno,
  propietario filtrado en SQL y respuesta sin identificador de actor.
- `SignedSandboxWebhookVerifierTest` comprueba firma HMAC-SHA256 sobre
  `timestamp + "." + rawBody`, ventanas ±300s, secreto fuerte,
  rechazo de firma corrupta, cuerpo alterado o demasiado grande.
- **Nuevo caso BKL-056:** un JSON semánticamente equivalente pero con
  bytes diferentes (espacios reinsertados) no reutiliza la firma; su
  propia firma sobre los bytes exactos sí es válida.
- El webhook continúa deshabilitado por defecto; solo se habilita en
  sandbox con secreto exterior de al menos 32 bytes.

## Comprobación

`mvn -B -ntp -f apps/api/pom.xml clean verify`

Para completar BKL-056, registrar además evidencia HTTP del webhook
falso o replay ante el controlador con webhook habilitado en la base de
test, sin usar secretos reales. Las pruebas unitarias del verificador no
demuestran por sí solas idempotencia en PostgreSQL ni protección de todos
los endpoints. Respetar precedencia BKL-049 y revisar código cruzadamente.
