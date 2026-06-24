package dev.kmpilot.todo.data

import app.cash.sqldelight.db.SqlDriver

/**
 * The per-platform persistent SqlDriver factory — the LAST deferred piece of the SQLDelight slice. Returns a
 * driver backed by durable storage so `buildRoot()` can persist tasks + users across app restarts ON DEVICE:
 *  - **android**: `AndroidSqliteDriver(schema, appContext, "todo.db")` — needs the app Context (see AppContextHolder);
 *    null until [dev.kmpilot.todo.MainActivity] seeds it.
 *  - **ios**: `NativeSqliteDriver(schema, "todo.db")`.
 *  - **jvm (desktop)**: a FILE-backed JDBC SQLite driver under the user's app data dir.
 *  - **wasm preview**: null — the preview deliberately stays in-memory/scenario-driven (no wasm SQL driver).
 *
 * When this returns null, `buildRoot()` falls back to the in-memory repo + in-memory user store. The schema,
 * queries, repository, and stores are otherwise identical `commonMain` code; only the driver is platform-specific.
 */
expect fun todoDriverOrNull(): SqlDriver?
