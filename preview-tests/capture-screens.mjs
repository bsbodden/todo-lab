// Capture a high-fidelity screenshot of each app screen → prototype/studio/screens/<Screen>.png.
// Used as node images in the minimap's "screenshot" mode. Regenerate after UI changes. Needs :8082 served.
import { chromium } from 'playwright';
import { mkdirSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, resolve } from 'path';
const BASE = process.env.BASE || 'http://127.0.0.1:8082';
const OUT = resolve(dirname(fileURLToPath(import.meta.url)), '../../../prototype/studio/screens');
mkdirSync(OUT, { recursive: true });

const b = await chromium.launch();
async function cap(file, nav) {
  const p = await b.newPage({ viewport: { width: 393, height: 852 }, deviceScaleFactor: 2 });
  await p.goto(BASE + '/app.html', { waitUntil: 'load', timeout: 60000 });
  await p.waitForTimeout(3500);          // let the shadow-root canvas paint
  if (nav) { await nav(p); await p.waitForTimeout(1300); }
  await p.screenshot({ path: `${OUT}/${file}` });
  await p.close();
  console.log('captured', file);
}
await cap('TaskList.png', null);
await cap('AddTask.png', async (p) => { await p.mouse.click(358, 795); });   // tap the FAB
await cap('TaskDetail.png', async (p) => { await p.mouse.click(220, 100); }); // tap the first task row
await b.close();
