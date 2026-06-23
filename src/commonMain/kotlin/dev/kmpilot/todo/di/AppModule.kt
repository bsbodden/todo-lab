package dev.kmpilot.todo.di

import dev.kmpilot.todo.data.InMemoryTaskRepository
import dev.kmpilot.todo.data.TaskRepository
import dev.kmpilot.todo.domain.Task
import kotlin.time.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * A clock seam: "now" as a wall-clock [LocalDateTime]. Injectable so tests/previews can pin time
 * instead of reading the system clock. (The domain stays pure; only the edges read the real clock.)
 */
fun interface Now {
    operator fun invoke(): LocalDateTime
}

/**
 * Cross-cutting singletons shared by EVERY platform (wasm preview, Android, iOS, jvm test harness).
 * The persistence adapter is bound by a SEPARATE module — that's the BYOK seam expressed as DI:
 * swap [inMemoryPersistenceModule] for the jvm SQLite module (or a future Supabase/Firebase module)
 * and nothing else in the app changes.
 */
val appModule: Module = module {
    single<Now> { Now { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) } }
}

/** Persistence binding for the preview + tests: in-memory. Swapping persistence == swapping this module. */
fun inMemoryPersistenceModule(seed: List<Task> = emptyList()): Module = module {
    single<TaskRepository> { InMemoryTaskRepository(seed) }
}
