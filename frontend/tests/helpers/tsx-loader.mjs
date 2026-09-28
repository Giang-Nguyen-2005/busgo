import { readFileSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import ts from "typescript";

// Compile real components for server-rendered tests using the existing compiler.
export function resolve(specifier, context, next) {
  if (specifier.startsWith(".") && context.parentURL?.includes("/src/")) {
    const url = new URL(specifier, context.parentURL);
    for (const suffix of [".ts", ".tsx"]) {
      if (existsSync(fileURLToPath(url) + suffix)) return next(url.href + suffix, context);
    }
  }
  return next(specifier, context);
}
export function load(url, context, next) {
  if (url.endsWith(".css")) return { format: "module", shortCircuit: true, source: "export default {};" };
  if (/\/src\/.*\.tsx?$/.test(url)) {
    const source = readFileSync(new URL(url), "utf8").replace("import.meta.env.VITE_API_BASE_URL", "undefined");
    return { format: "module", shortCircuit: true, source: ts.transpileModule(source, {
      fileName: fileURLToPath(url),
      compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.ReactJSX },
    }).outputText };
  }
  return next(url, context);
}
