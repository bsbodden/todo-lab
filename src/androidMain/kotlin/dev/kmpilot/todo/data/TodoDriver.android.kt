package dev.kmpilot.todo.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.kmpilot.todo.db.TodoDatabase

/**
 * Android persistence: an [AndroidSqliteDriver] over a "todo.db" file in the app's private storage. Needs the app
 * [android.content.Context], seeded into [AppContextHolder] by MainActivity before buildRoot runs. Returns null if
 * the context isn't set yet (preview/test) so buildRoot falls back to the in-memory stack instead of crashing.
 */
actual fun todoDriverOrNull(): SqlDriver? {
    val context = AppContextHolder.appContext ?: return null
    return AndroidSqliteDriver(TodoDatabase.Schema, context, "todo.db")
}
