// Model-based UI test — drives the TaskList STATECHART with real taps in the wasm phone-frame preview and
// asserts the screen's LOGICAL state (not pixels) via the `__screen` bridge (TaskListMachine.publishScreenState).
//
// This is the device-level half of the test-first story: state transitions ARE user interactions, replayed as
// real gestures on the simulated device, with the statechart's state assertable. Toggling each task completes it
// (the list hides completed), so the chart walks Content:3 -> Content:2 -> Content:1 -> Empty.
//
// Run:
//   1. build + serve the preview:  ./gradlew wasmJsBrowserDevelopmentExecutableDistribution
//      python3 ../../docs/kmp-corpus/serve-wasm.py 8082 build/dist/wasmJs/developmentExecutable
//   2. npm i playwright && npx playwright install chromium
//   3. node preview-tests/model-based-ui.mjs
import { chromium } from 'playwright';

const BASE = process.env.BASE || 'http://127.0.0.1:8082';
const EXPECTED = ['TaskList=Content:3', 'TaskList=Content:2', 'TaskList=Content:1', 'TaskList=Empty'];

const b = await chromium.launch();
const p = await b.newPage({ viewport: { width: 980, height: 1000 }, deviceScaleFactor: 2 });
await p.goto(BASE + '/index.html', { waitUntil: 'load', timeout: 60000 });
await p.waitForFunction(() => { const l = document.getElementById('loading'); return l && getComputedStyle(l).display === 'none'; }, { timeout: 25000 }).catch(() => {});
await p.waitForTimeout(1500);

const fh = await (await p.$('#frame')).contentFrame();          // the app runs in the iframe
const screen = await p.$('#screen');
const box = await screen.boundingBox();
const readState = async () => { for (let i = 0; i < 30; i++) { const s = await fh.evaluate(() => globalThis.__screen); if (s) return s; await p.waitForTimeout(100); } return null; };
const tapFirstCheckbox = async () => { await p.mouse.click(box.x + 41, box.y + 100); await p.waitForTimeout(900); }; // completing removes it → first row is always next

const got = [];
got.push(await readState());
for (let i = 0; i < 3; i++) { await tapFirstCheckbox(); got.push(await readState()); }
got.forEach((s, i) => console.log(`  step ${i}: ${s}`));

const pass = JSON.stringify(got) === JSON.stringify(EXPECTED);
console.log(pass ? 'PASS — statechart drove Content:3 → Empty via real taps' : `FAIL — expected ${JSON.stringify(EXPECTED)}`);
await b.close();
process.exit(pass ? 0 : 1);
