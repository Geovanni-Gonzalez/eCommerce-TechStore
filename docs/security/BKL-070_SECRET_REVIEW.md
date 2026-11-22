# BKL-070 — Control de secretos en Git

El repositorio se encuentra público en GitHub. Se añadió una comprobación
en CI (`node scripts/ci/check-secret-files.mjs`) que inspecciona
**archivos actualmente rastreados** por Git y falla al detectar:

- `.env` reales (excepto muestras terminadas en `.example`,
  `.sample` o `.template`), archivos `.pem/.key/.p12/.pfx`.
- Marcadores de claves privadas PEM, tokens GitHub conocidos, tokens API
  en formato `sk-...` y claves de acceso AWS tipo AKIA.
- Ningún valor sospechoso se imprime: únicamente ruta y categoría.

Prueba de reglas: `node --test scripts/ci/check-secret-files.test.mjs`.

## Verificaciones aún obligatorias

- [ ] Auditoría del **historial completo** de commits, PRs y artifacts
  (este script solo revisa el estado rastreado actual).
- [ ] Revisar manualmente credenciales de CI, valores `.env.example`,
  logs de acciones, capturas y cualquier dato privado de terceros.
- [ ] Si aparece un secreto, **revocarlo y rotarlo** antes de limpiarlo
  del historial; borrar el archivo solamente no protege un token filtrado.
- [ ] Verificar en sesión anónima la visibilidad pública del repositorio
  y que el README tenga instalación reproducible.
- [ ] Revisar por segundo integrante antes de declarar BKL-070 Done.

La búsqueda por patrones es defensiva, no un escáner exhaustivo ni
prueba de ausencia de secretos. Los ejemplos deliberados de test deben
continuar explícitamente rotulados como valores de CI que no sirven
fuera de entornos efímeros.
