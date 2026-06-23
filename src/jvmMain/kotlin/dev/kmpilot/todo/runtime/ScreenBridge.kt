package dev.kmpilot.todo.runtime

/** No-op off-wasm (the jvm target is a unit-test harness, not a host with a JS global). */
actual fun publishScreenState(label: String, state: String) { /* no-op */ }

actual fun publishChartSpec(json: String) { /* no-op */ }

actual fun publishAppGraph(json: String) { /* no-op */ }

actual fun publishCurrentScreen(name: String) { /* no-op */ }
