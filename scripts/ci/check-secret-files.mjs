/**
 * BKL-070: protección sin dependencias para archivos rastreados por Git.
 * Solo imprime nombre de archivo y tipo de hallazgo; jamás el secreto.
 */
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

export function unsafePath(path) {
  const normalized = path.replaceAll("\\", "/");
  const leaf = normalized.split("/").at(-1);
  if (!leaf) return false;
  if (/^\.env(?:\..+)?$/.test(leaf)
      && !/\.(?:example|sample|template)$/.test(leaf)) return true;
  return /\.(?:pem|p12|pfx|key)$/i.test(leaf);
}

export function secretFinding(source) {
  if (/-----BEGIN [A-Z ]*PRIVATE KEY-----/.test(source)) return "private-key";
  if (/gh[pousr]_[A-Za-z0-9]{36,}/.test(source)) return "github-token";
  if (/sk-(?:proj-)?[A-Za-z0-9_-]{24,}/.test(source)) return "api-token";
  if (/AKIA[0-9A-Z]{16}/.test(source)) return "aws-access-key";
  return null;
}

export function scanTrackedFiles(root) {
  const names = execFileSync("git", ["ls-files", "-z"], {
    cwd: root, encoding: "utf8",
  }).split("\0").filter(Boolean);
  const findings = [];
  for (const path of names) {
    if (unsafePath(path)) {
      findings.push({ path, category: "sensitive-file" });
      continue;
    }
    const bytes = readFileSync(resolve(root, path));
    if (bytes.length > 2_000_000 || bytes.includes(0)) continue;
    const category = secretFinding(bytes.toString("utf8"));
    if (category) findings.push({ path, category });
  }
  return findings;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const findings = scanTrackedFiles(process.cwd());
  if (findings.length > 0) {
    for (const { path, category } of findings) {
      console.error("Archivo sospechoso:", path, "(", category, ")");
    }
    console.error("Revisar antes de publicar: se omiten valores sensibles en logs.");
    process.exitCode = 1;
  } else {
    console.log("BKL-070: archivos Git rastreados sin coincidencias de alto riesgo.");
  }
}
