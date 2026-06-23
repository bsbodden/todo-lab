package dev.kmpilot.todo.runtime

// The editor bridge is a wasm-preview concern only; on the real iOS target it is a no-op.
actual fun publishScreenState(label: String, state: String) { /* no-op */ }
actual fun publishChartSpec(json: String) { /* no-op */ }
actual fun publishAppGraph(json: String) { /* no-op */ }
actual fun publishCurrentScreen(name: String) { /* no-op */ }
