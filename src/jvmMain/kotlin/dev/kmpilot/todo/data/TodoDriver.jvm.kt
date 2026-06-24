package dev.kmpilot.todo.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.kmpilot.todo.db.TodoDatabase
import java.io.File

/**
 * Desktop persistence: a FILE-backed JDBC SQLite driver under the user's app-data dir, so the JVM runner persists
 * too (the in-memory [jvmSqliteDriver] stays the unit-test driver). The schema is created only for a brand-new
 * file; reopening an existing DB skips it so prior rows survive.
 */
actual fun todoDriverOrNull(): SqlDriver? {
    val dir = File(System.getProperty("user.home"), ".todo-lab").apply { mkdirs() }
    return fileBackedDriver(File(dir, "todo.db"))
}

/** Shared by the actual factory and the restart-persistence test: create-on-first-open, reuse thereafter. */
internal fun fileBackedDriver(file: File): SqlDriver {
    val fresh = !file.exists()
    val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
    if (fresh) TodoDatabase.Schema.create(driver)
    return driver
}
