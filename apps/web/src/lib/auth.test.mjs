import test from "node:test";
import assert from "node:assert/strict";
import {
  parseAuthSession, parseAuthUser, parseRegisteredClient,
  roleDestination, validateRegistration,
} from "./auth.ts";

const client = { id: 13, displayName: "Ana Pérez", email: "ana@example.com", role: "CLIENT" };
const admin = { ...client, id: 14, role: "ADMIN" };
const session = { accessToken: "A".repeat(43), tokenType: "Bearer", user: client };

test("BKL-017 valida identidad CLIENT/ADMIN desde backend", () => {
  assert.deepEqual(parseAuthUser(client), client);
  assert.equal(parseAuthSession(session).user.role, "CLIENT");
  assert.equal(parseAuthSession({...session, user: admin}).user.role, "ADMIN");
});

test("BKL-017 redirige por rol verificado y no por parámetros del navegador", () => {
  assert.equal(roleDestination("CLIENT"), "/cuenta");
  assert.equal(roleDestination("ADMIN"), "/admin");
});

test("BKL-017 descarta tokens vacíos, JWT y roles arbitrarios", () => {
  for (const invalid of [
    {...session, accessToken:""},
    {...session, accessToken:"eyJ.abc.def"},
    {...session, tokenType:"Basic"},
    {...session, user:{...client, role:"SUPERADMIN"}},
    {...session, user:{...client, id:-1}},
  ]) assert.throws(() => parseAuthSession(invalid));
});

test("BKL-017 registro público exige CLIENT sin aceptar role ADMIN", () => {
  assert.deepEqual(parseRegisteredClient(client),client);
  assert.throws(() => parseRegisteredClient(admin));
  assert.throws(() => parseRegisteredClient({...client, email:""}));
});

test("BKL-017 valida nombre, email y contraseña dentro de límites backend", () => {
  const valid = {displayName:"Ana Pérez",email:"ana@example.com",password:"ClaveSegura2026"};
  assert.equal(validateRegistration(valid),null);
  for (const input of [
    {...valid,displayName:"A"},
    {...valid,email:"invalid"},
    {...valid,password:"short"},
    {...valid,password:"á".repeat(40)},
    {...valid,password:"x".repeat(129)},
    {...valid,password:"abc\n123456"},
  ]) assert.ok(validateRegistration(input));
});
