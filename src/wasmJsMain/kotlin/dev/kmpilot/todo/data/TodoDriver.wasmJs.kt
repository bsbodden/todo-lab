package dev.kmpilot.todo.data

import app.cash.sqldelight.db.SqlDriver

/**
 * The wasm preview deliberately has NO SQL driver — it stays in-memory/scenario-driven (see wasmJsMain/main.kt).
 * Returning null makes buildRoot fall back to the in-memory repo + user store, exactly as the preview wants.
 */
actual fun todoDriverOrNull(): SqlDriver? = null
