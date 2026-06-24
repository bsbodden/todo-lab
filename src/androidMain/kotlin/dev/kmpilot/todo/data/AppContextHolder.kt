package dev.kmpilot.todo.data

import android.content.Context

/**
 * A tiny holder for the application [Context] the Android SqlDriver needs. Seeded in
 * [dev.kmpilot.todo.MainActivity].onCreate BEFORE setContent { buildRoot() }, so [todoDriverOrNull] can build the
 * AndroidSqliteDriver. If unset (e.g. a Compose preview or a unit test that never went through MainActivity), the
 * factory returns null and buildRoot falls back to the in-memory stack — no crash.
 */
object AppContextHolder {
    var appContext: Context? = null
}
