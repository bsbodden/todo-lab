// Editor interaction tests (LIVE, end-to-end) — drives the studio the way a user does and asserts the app
// follows: (1) click-to-navigate (click a screen node → the live app navigates there), (2) double-click
// drill-down (a screen's state machine takes center stage + the screen map minimizes to the corner overview),
// (3) toggle back to the screen map. Needs BOTH preview (:8082) + studio (:8083) served (run.sh).
import { chromium } from 'playwright';

const STUDIO = process.env.STUDIO_BASE || 'http://127.0.0.1:8083';

const b = await chromium.launch();
const p = await b.newPage({ viewport: { width: 1280, height: 820 } });
const errs = [];
p.on('pageerror', (e) => errs.push(e.message));

const results = [];
const check = (name, cond) => { results.push(!!cond); console.log((cond ? '  ok   ' : ' FAIL  ') + name); };
const nodePt = (id) => p.evaluate((nid) => {
  const m = window.__minimap; const n = m.nodes[nid]; const c = n.getBBox().center();
  const q = m.paper.localToClientPoint(c.x, c.y); return { x: q.x, y: q.y };
}, id);
const centerNodes = () => p.evaluate(() => Object.keys(window.__minimap.nodes).sort());

await p.goto(STUDIO + '/index.html', { waitUntil: 'load', timeout: 60000 });
await p.waitForFunction(() => window.__minimap && Object.keys(window.__minimap.nodes).length === 3 && window.__activeState, { timeout: 35000 });
await p.waitForTimeout(2800); // let the app's chartSpec (the TaskList state machine) arrive

// (1) click-to-navigate — click the AddTask node, the live app should navigate to AddTask
let pt = await nodePt('AddTask');
await p.mouse.click(pt.x, pt.y);
await p.waitForFunction(() => window.__activeState === 'AddTask', { timeout: 8000 }).catch(() => {});
check('click AddTask navigates the live app to AddTask', await p.evaluate(() => window.__activeState === 'AddTask'));

// return to TaskList (the screen with the rich state machine) for the drill-down
pt = await nodePt('TaskList');
await p.mouse.click(pt.x, pt.y);
await p.waitForFunction(() => window.__activeState === 'TaskList', { timeout: 8000 }).catch(() => {});

// (2) drill-down — double-click TaskList → its state machine center stage + corner overview
pt = await nodePt('TaskList');
await p.mouse.dblclick(pt.x, pt.y);
await p.waitForFunction(() => Object.keys(window.__minimap.nodes).sort().join(',') === 'Content,Empty,Error,Loading'
  && document.querySelectorAll('#corner .joint-cell').length >= 3, { timeout: 8000 }).catch(() => {});
const drill = await p.evaluate(() => ({
  center: Object.keys(window.__minimap.nodes).sort(),
  corner: document.getElementById('corner').classList.contains('show'),
  cornerCells: document.querySelectorAll('#corner .joint-cell').length,
  back: getComputedStyle(document.getElementById('backBtn')).display,
}));
check('dbl-click TaskList shows its state machine (Content/Empty/Error/Loading)',
  JSON.stringify(drill.center) === JSON.stringify(['Content', 'Empty', 'Error', 'Loading']));
check('drill shows the corner overview with the screen map (>=3 cells)', drill.corner && drill.cornerCells >= 3);
check('drill shows the back button', drill.back !== 'none');

// (3) toggle back to the screen map
await p.click('#backBtn');
await p.waitForFunction(() => Object.keys(window.__minimap.nodes).sort().join(',') === 'AddTask,TaskDetail,TaskList', { timeout: 8000 }).catch(() => {});
const back = await p.evaluate(() => ({
  center: Object.keys(window.__minimap.nodes).sort(),
  corner: document.getElementById('corner').classList.contains('show'),
}));
check('back restores the screen map', JSON.stringify(back.center) === JSON.stringify(['AddTask', 'TaskDetail', 'TaskList']));
check('back hides the corner overview', !back.corner);

check('no page errors', errs.length === 0);

const ok = results.every(Boolean);
console.log('INTERACTIONS:', ok ? 'PASS' : 'FAIL', errs.length ? '| errors: ' + JSON.stringify(errs.slice(0, 3)) : '');
await b.close();
process.exit(ok ? 0 : 1);
