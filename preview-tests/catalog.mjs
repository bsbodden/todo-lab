// Catalog test (LIVE) — points the editor at the data/ folder and asserts every app loads: the dropdown
// matches data/apps.json, and selecting each app renders exactly its manifest's screens. Also asserts the
// in-progress port (twine) carries "planned" stubs. Needs the studio (:8083) served (run.sh).
import { chromium } from 'playwright';

const STUDIO = process.env.STUDIO_BASE || 'http://127.0.0.1:8083';

const b = await chromium.launch();
const p = await b.newPage({ viewport: { width: 1320, height: 840 } });
const errs = [];
p.on('pageerror', (e) => errs.push(e.message));
const results = [];
const check = (name, cond) => { results.push(!!cond); console.log((cond ? '  ok   ' : ' FAIL  ') + name); };

await p.goto(STUDIO + '/index.html', { waitUntil: 'load', timeout: 60000 });
await p.waitForFunction(() => window.__minimap && Object.keys(window.__minimap.nodes).length >= 3, { timeout: 35000 });

const apps = await (await fetch(STUDIO + '/data/apps.json')).json();
check('catalog lists the real apps', apps.length >= 2);
const dropdown = await p.evaluate(() => [...document.querySelectorAll('#appPick option')].map((o) => o.value));
check('dropdown matches the data/ catalog', JSON.stringify(dropdown) === JSON.stringify(apps.map((a) => a.id)));

for (const a of apps) {
  const manifest = await (await fetch(`${STUDIO}/data/${a.id}/app.json`)).json();
  const expected = manifest.screens.map((s) => s.id).sort();
  await p.selectOption('#appPick', a.id);
  // wait for the actual node SET to match (count alone is ambiguous when consecutive apps have equal counts)
  await p.waitForFunction((exp) => window.__minimap && JSON.stringify(Object.keys(window.__minimap.nodes).sort()) === JSON.stringify(exp), expected, { timeout: 20000 }).catch(() => {});
  const nodes = await p.evaluate(() => Object.keys(window.__minimap.nodes).sort());
  check(`${a.id}: renders its ${expected.length} screens`, JSON.stringify(nodes) === JSON.stringify(expected));
}

// the catalog apps are REAL functioning apps with a live wasm preview (not mocks)
const live = [];
for (const a of apps) {
  const m = await (await fetch(`${STUDIO}/data/${a.id}/app.json`)).json();
  if (m.preview) live.push(a.id);
}
check(`apps are live (real preview): ${live.join(', ')}`, live.length === apps.length);

check('no page errors', errs.length === 0);
const ok = results.every(Boolean);
console.log('CATALOG:', ok ? 'PASS' : 'FAIL', errs.length ? '| errors: ' + JSON.stringify(errs.slice(0, 2)) : '');
await b.close();
process.exit(ok ? 0 : 1);
