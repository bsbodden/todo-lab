package dev.kmpilot.todo.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import dev.kmpilot.todo.db.TodoDatabase

/**
 * iOS persistence: a [NativeSqliteDriver] over a "todo.db" file in the app sandbox (the driver resolves the path
 * under Application Support). No Context plumbing needed — the schema/queries/repository are identical commonMain.
 */
actual fun todoDriverOrNull(): SqlDriver? = NativeSqliteDriver(TodoDatabase.Schema, "todo.db")
