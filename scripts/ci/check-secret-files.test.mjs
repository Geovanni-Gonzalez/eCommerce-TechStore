import test from "node:test";
import assert from "node:assert/strict";
import { unsafePath, secretFinding } from "./check-secret-files.mjs";

test("no permitir archivos privados ni claves versionadas", () => {
  for (const path of [".env", "apps/api/.env.local", "infrastructure/.env.staging",
                      "keys/server.key", "keys/client.p12"]) {
    assert.equal(unsafePath(path), true, path);
  }
  for (const path of ["apps/web/.env.example", "infrastructure/.env.sample",
                      "README.md", "docs/app.env.template"]) {
    assert.equal(unsafePath(path), false, path);
  }
});
test("detectar secretos sin imprimirlos y evitar falsos positivos comunes", () => {
  assert.equal(secretFinding("ghp_"+"X".repeat(36)), "github-token");
  assert.equal(secretFinding("sk-"+"A".repeat(30)), "api-token");
  assert.equal(secretFinding("AKIA"+"B".repeat(16)), "aws-access-key");
  assert.equal(secretFinding("-----BEGIN "+"RSA PRIVATE KEY-----"), "private-key");
  assert.equal(secretFinding("techstore_ci_only / placeholder / sk-example"), null);
});
