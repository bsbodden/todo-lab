package dev.kmpilot.todo.di

import dev.kmpilot.todo.data.SqlDelightTaskRepository
import dev.kmpilot.todo.data.SqliteTaskRepository
import dev.kmpilot.todo.data.TaskRepository
import dev.kmpilot.todo.data.jvmSqliteDriver
import dev.kmpilot.todo.data.todoDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The jvm persistence binding — the opinionated default is **SQLDelight** (the local-SQL leader). This is
 * the ONLY difference from the preview wiring: same [appModule], different persistence module. In a full
 * KMP app the Android/iOS source sets bind their own SqlDriver the same way; a BYOK remote backend is just
 * another module. (`jvmSqlitePersistenceModule` keeps the raw-JDBC adapter available as a reference alt.)
 */
val jvmPersistenceModule: Module = module {
    single<TaskRepository> { SqlDelightTaskRepository(todoDatabase(jvmSqliteDriver())) }
}

/** Reference alternate: the hand-rolled raw-JDBC adapter (superseded as default by SQLDelight). */
val jvmSqlitePersistenceModule: Module = module {
    single<TaskRepository> { SqliteTaskRepository() }
}
