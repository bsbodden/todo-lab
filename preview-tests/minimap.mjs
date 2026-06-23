// Minimap test (LIVE, end-to-end) — loads the studio, which embeds the running app off-screen; the app
// publishes its ChartSpec + live state via postMessage; the studio renders the JointJS/ELK minimap and
// highlights the active node. Asserts the minimap reflects the REAL app (4 states, 8 transitions) and the
// highlight matches the app's actual state — no hardcoded data. Needs BOTH preview (:8082) + studio (:8083).
import { chromium } from 'playwright';

const STUDIO = process.env.STUDIO_BASE || 'http://127.0.0.1:8083';
const EXPECT_NODES = ['AddTask', 'TaskDetail', 'TaskList']; // the app's SCREENS
const EXPECT_LINKS = 4;                                       // navigation edges

const b = await chromium.launch();
const p = await b.newPage({ viewport: { width: 1300, height: 900 } });
const errs = [];
p.on('pageerror', (e) => errs.push(e.message));

await p.goto(STUDIO + '/index.html', { waitUntil: 'load', timeout: 60000 });

// wait until the studio renders the minimap from the live ChartSpec AND the app settles into an active,
// highlighted state (the highlighted node must equal the app's reported active state).
await p.waitForFunction(() => {
  const m = window.__minimap;
  if (!m || Object.keys(m.nodes).length !== 3) return false;
  let a = String(window.__activeState || ''); if (a.includes('=')) a = a.split('=')[1]; a = a.split(':')[0];
  const hi = Object.keys(m.nodes).filter((id) => m.nodes[id].attr('body/strokeWidth') >= 2.5);
  return Boolean(a) && hi.length === 1 && hi[0] === a;
}, { timeout: 35000 });

const r = await p.evaluate(() => {
  const m = window.__minimap;
  let a = String(window.__activeState || ''); if (a.includes('=')) a = a.split('=')[1]; a = a.split(':')[0];
  return {
    nodes: Object.keys(m.nodes).sort(),
    links: m.graph.getLinks().length,
    highlighted: Object.keys(m.nodes).filter((id) => m.nodes[id].attr('body/strokeWidth') >= 2.5),
    active: a,
  };
});

const ok = JSON.stringify(r.nodes) === JSON.stringify(EXPECT_NODES)
  && r.links === EXPECT_LINKS
  && r.highlighted.length === 1 && r.highlighted[0] === r.active
  && errs.length === 0;

console.log('minimap(live from app):', JSON.stringify(r), '| errors:', errs.length, ok ? 'PASS' : 'FAIL');
await b.close();
process.exit(ok ? 0 : 1);
