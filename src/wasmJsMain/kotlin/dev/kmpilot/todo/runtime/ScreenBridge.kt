package dev.kmpilot.todo.runtime

/** Writes the logical screen state to `globalThis.__screen` (read by the model-based UI test / studio). */
actual fun publishScreenState(label: String, state: String) {
    js("globalThis.__screen = label + '=' + state")
}

actual fun publishChartSpec(json: String) {
    js("globalThis.__chartSpec = json")
}

actual fun publishAppGraph(json: String) {
    js("globalThis.__appGraph = json")
}

actual fun publishCurrentScreen(name: String) {
    js("globalThis.__currentScreen = name")
}
