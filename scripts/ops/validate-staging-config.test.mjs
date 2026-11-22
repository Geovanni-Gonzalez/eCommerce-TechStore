import test from "node:test";
import assert from "node:assert/strict";
import {validateStagingConfig} from "./validate-staging-config.mjs";

const valid = {
  STAGING_WEB_URL:"https://staging.example.test",
  STAGING_API_URL:"https://api-staging.example.test",
  NEXT_PUBLIC_API_URL:"https://api-staging.example.test",
  CORS_ALLOWED_ORIGINS:"https://staging.example.test",
  DB_URL:"jdbc:postgresql://db-staging.example.test:5432/techstore_staging",
  DB_USERNAME:"techstore_staging",
  SPRING_DATASOURCE_PASSWORD:"example-only-strong-password",
  TECHSTORE_PAYMENT_PROVIDER:"DISABLED",
  TECHSTORE_PAYMENT_WEBHOOK_ENABLED:"false",
  TECHSTORE_PAYMENT_SANDBOX_AUTO_CONFIRM:"false",
};
test("staging seguro acepta URLs HTTPS, DB separada y pagos desactivados",()=>{
  assert.deepEqual(validateStagingConfig(valid),[]);
});
test("staging bloquea HTTP, CORS abierto, DB test y confirmación habilitada",()=>{
  const unsafe={...valid,
    STAGING_WEB_URL:"http://localhost:3000",
    CORS_ALLOWED_ORIGINS:"*",
    DB_URL:"jdbc:postgresql://localhost:5432/techstore_test",
    TECHSTORE_PAYMENT_WEBHOOK_ENABLED:"true",
  };
  const errors=validateStagingConfig(unsafe);
  assert.ok(errors.length>=4);
  assert.ok(errors.every(x=>!x.includes("example-only-strong-password")));
});
