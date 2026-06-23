package dev.kmpilot.todo.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.kmpilot.todo.db.TodoDatabase

/**
 * The jvm/test SQLDelight driver — in-memory SQLite via JDBC. A real device app swaps only this factory
 * (Android: `AndroidSqliteDriver(schema, context, "todo.db")`; iOS: `NativeSqliteDriver(...)`); the schema,
 * queries, repository, and tests are identical `commonMain` code.
 */
fun jvmSqliteDriver(): SqlDriver =
    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { TodoDatabase.Schema.create(it) }
