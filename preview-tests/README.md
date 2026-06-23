# preview-tests — device-level tests for the wasm mobile preview

These drive the **real wasm mobile preview** in a headless browser (Playwright) and assert the app's
**logical statechart state** — via the `__screen` bridge (`TaskListMachine.publishScreenState`) — not pixels.
This is the device half of the test-first story: *state transitions are user interactions*, replayed as real
gestures on the simulated device.

| File | What it tests |
|---|---|
| `model-based-ui.mjs` | drives the TaskList statechart with real **taps**; asserts it walks `Content:3 → Content:2 → Content:1 → Empty` |
| `scenarios.mjs` | boots each `?scenario=NAME` **precondition** and asserts the resulting state (`default` / `empty` / `many` / `error`) |
| `minimap.mjs` | loads the **editor** (`prototype/studio`, JointJS/ELK) and asserts the **screen map** is rendered live from the app (3 screens, 4 nav edges) and **highlights the current screen** |
| `interactions.mjs` | drives the editor like a user: **click-to-navigate** (click a screen node → the live app navigates there), **double-click drill-down** (a screen's state machine takes center stage + the screen map minimizes to the corner overview), and **toggle back** |
| `catalog.mjs` | points the editor at the **`data/` app catalog** and asserts every app loads (dropdown matches `apps.json`; each app renders exactly its manifest's screens) and that the apps are **real (live wasm preview)** |
| `capture-screens.mjs` | (utility) captures a real screenshot of each live screen → `data/<app>/screens/*.png` |

## Run it — one command (builds, serves, tests, tears down)
```bash
./preview-tests/run.sh
```

## Run against an already-served preview
```bash
# from lab/todo-lab, build + serve once:
./gradlew wasmJsBrowserDevelopmentExecutableDistribution
python3 ../../docs/kmp-corpus/serve-wasm.py 8082 build/dist/wasmJs/developmentExecutable &

# then, in preview-tests/:
npm install && npx playwright install chromium
BASE=http://127.0.0.1:8082 npm test          # or: npm run test:scenarios
```

Requirements: Node + npm, Playwright (`npm install`) + Chromium (`npx playwright install chromium`),
and JDK 21 + the project's `./gradlew` for the build step. `BASE` defaults to `http://127.0.0.1:8082`.

## The editor stage
`run.sh` also serves the studio (`prototype/studio`, on :8083) and runs `minimap.mjs` + `interactions.mjs`.
The map is **`ChartSpec` → ELK (layered, orthogonal routing) → JointJS core** (open-source; renderer decided
in `docs/research/09`). The editor is **screen map (left) ⇄ live phone (right)**: nodes are screens (rectangles
or high-fidelity phone screenshots, toggleable), edges are navigation; click a node to navigate the live app,
double-click to drill into that screen's state machine (the screen map shrinks to a corner overview). The app
publishes its screen graph + current screen + per-screen `ChartSpec` over `postMessage`, so the tests
cross-check the rendered map and the highlighted node against the running app's actual state.
