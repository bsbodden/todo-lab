package dev.kmpilot.todo.runtime

/**
 * Test/debug bridge — publishes the active screen's LOGICAL state (the statechart's current state) to the
 * host environment so automated harnesses, the studio, and the dev console can assert *state*, not just
 * pixels. On wasm it writes `globalThis.__screen`; everywhere else it's a no-op. This is the read half of
 * the device-testing surface (the write half — fire(event)/setScenario — comes next).
 */
expect fun publishScreenState(label: String, state: String)

/** Publishes the screen's statechart structure (the owned [ChartSpec], as JSON) for the per-screen drill-down. */
expect fun publishChartSpec(json: String)

/** Publishes the APP graph (screens as nodes, navigation as edges) — the top-level minimap. */
expect fun publishAppGraph(json: String)

/** Publishes the currently-active screen name (so the minimap highlights it and the editor follows nav). */
expect fun publishCurrentScreen(name: String)
