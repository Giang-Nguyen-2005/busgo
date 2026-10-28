// Reuse milestone acceptance assertions, relocate evidence, suppress redundant images.
// No assertion or API fixture is changed. Compile with the original filename so
// relative imports keep their original meaning. All HTTP APIs remain intercepted.
const fs = require('node:fs'), path = require('node:path'), Module = require('node:module');
const suite = process.argv[2];
const sources = {
  customer: 'frontend/tests/m29-customer-browser.cjs',
  operator: '.tools/m29-browser/m29-3/acceptance.cjs',
  platform: 'frontend/tests/m29-4-final.cjs',
};
if (!sources[suite]) throw Error('Choose customer, operator, or platform');
const filename = path.resolve(sources[suite]);
const out = path.resolve('.tools/m29-browser/m29-5/fixtures-' + suite);
fs.mkdirSync(out, {recursive:true});
let source = fs.readFileSync(filename, 'utf8');
source = source.replace(/const out\s*=\s*[^;]+;/, `const out=${JSON.stringify(out)};`)
  .replaceAll('http://127.0.0.1:5188', 'http://127.0.0.1:5195')
  .replaceAll('http://127.0.0.1:5190', 'http://127.0.0.1:5195');
// Keep all viewport assertions; screenshots of live integration are collected separately.
source = source.replaceAll('await p.screenshot(', 'await (async () => {})(');
source = source.replaceAll('await page.screenshot(', 'await (async () => {})(');
fs.writeFileSync(path.join(out, 'provenance.json'), JSON.stringify({source:sources[suite], screenshots:false, mode:'fixtures only'},null,2));
const mod = new Module(filename, module); mod.filename=filename;
mod.paths=Module._nodeModulePaths(path.dirname(filename)); mod._compile(source, filename);
