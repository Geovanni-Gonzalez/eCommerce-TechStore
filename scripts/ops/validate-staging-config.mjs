/**
 * BKL-060: validación estática de configuración staging.
 * No abre red, no despliega y NUNCA imprime valores de variables.
 */
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

function httpsOrigin(value) {
  if (!value) return null;
  try {
    const parsed = new URL(value);
    if (parsed.protocol !== "https:" || !parsed.hostname ||
        ["localhost","127.0.0.1","::1"].includes(parsed.hostname) ||
        parsed.username || parsed.password || parsed.search || parsed.hash ||
        (parsed.pathname !== "/" && parsed.pathname !== "")) return null;
    return parsed.origin;
  } catch { return null; }
}

export function validateStagingConfig(env) {
  const problems=[];
  const web=httpsOrigin(env.STAGING_WEB_URL);
  const api=httpsOrigin(env.STAGING_API_URL);
  const publicApi=httpsOrigin(env.NEXT_PUBLIC_API_URL);
  if (!web) problems.push("STAGING_WEB_URL exige HTTPS público válido");
  if (!api) problems.push("STAGING_API_URL exige HTTPS público válido");
  if (!publicApi || publicApi !== api)
    problems.push("NEXT_PUBLIC_API_URL debe apuntar a la API HTTPS staging");
  const cors=(env.CORS_ALLOWED_ORIGINS??"").split(",").map(x=>x.trim()).filter(Boolean);
  if (!web || cors.length!==1 || cors[0]!==web)
    problems.push("CORS_ALLOWED_ORIGINS debe restringirse al origen web de staging");
  const database=env.DB_URL??"";
  if (!/^jdbc:postgresql:\/\/[^/\s:@]+(?::\d+)?\/[^/?#\s]+(?:\?[^\s#]*)?$/.test(database) ||
      /(?:localhost|127\.0\.0\.1|techstore_test)/i.test(database))
    problems.push("DB_URL requiere PostgreSQL staging remoto y separado de test");
  if (!env.DB_USERNAME?.trim() || !env.SPRING_DATASOURCE_PASSWORD ||
      env.SPRING_DATASOURCE_PASSWORD.length<16)
    problems.push("Falta identidad/clave DB fuerte en secretos del proveedor");
  if (!["DISABLED","SANDBOX"].includes(env.TECHSTORE_PAYMENT_PROVIDER??""))
    problems.push("Solo se admite DISABLED o SANDBOX en este staging académico");
  if (env.TECHSTORE_PAYMENT_WEBHOOK_ENABLED==="true" ||
      env.TECHSTORE_PAYMENT_SANDBOX_AUTO_CONFIRM==="true")
    problems.push("No habilitar webhooks ni autoconfirmación sin revisión específica");
  return problems;
}

if (process.argv[1] && resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
  const issues=validateStagingConfig(process.env);
  if (issues.length) {
    issues.forEach(item=>console.error("STAGING:",item));
    process.exitCode=1;
  } else {
    console.log("Configuración staging validada sin mostrar valores ni credenciales.");
  }
}
