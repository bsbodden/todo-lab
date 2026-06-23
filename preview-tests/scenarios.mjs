// Scenario / precondition verification — boots the wasm app into each named precondition (?scenario=NAME)
// and asserts the statechart lands in the expected state (via the __screen bridge). Scenarios are the
// "LLM seeds a precondition, see it in the frame" loop: a fixture (+ entry state) the app starts in.
//   default → Content:3   empty → Empty   many → Content:25   error → Error (FailingTaskRepository)
// Run (after building + serving the preview):  node preview-tests/scenarios.mjs
import { chromium } from 'playwright';

const BASE = process.env.BASE || 'http://127.0.0.1:8082';
const CASES = [
  ['default', 'TaskList=Content:3'],
  ['empty',   'TaskList=Empty'],
  ['many',    'TaskList=Content:25'],
  ['error',   'TaskList=Error:Simulated backend failure'],
];

const b = await chromium.launch();
let allPass = true;
for (const [name, expected] of CASES) {
  const p = await b.newPage({ viewport: { width: 393, height: 852 } });
  await p.goto(`${BASE}/app.html?scenario=${name}`, { waitUntil: 'load', timeout: 60000 });
  let screen = null, scenario = null;
  for (let i = 0; i < 40; i++) { screen = await p.evaluate(() => globalThis.__screen); scenario = await p.evaluate(() => globalThis.__scenario); if (screen) break; await p.waitForTimeout(150); }
  const ok = screen === expected && scenario === name;
  if (!ok) allPass = false;
  console.log(`  ${name.padEnd(8)} scenario=${scenario}  __screen=${screen}  ${ok ? 'OK' : 'FAIL expected ' + expected}`);
  await p.close();
}
console.log(allPass ? 'SCENARIOS: PASS' : 'SCENARIOS: FAIL');
await b.close();
process.exit(allPass ? 0 : 1);
