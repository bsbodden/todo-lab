package dev.kmpilot.todo.di

import dev.kmpilot.todo.data.TaskRepository
import dev.kmpilot.todo.domain.Task
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** The DI graph must resolve every edge the entry points pull — caught here, not at app launch. */
class AppModuleTest {

    @AfterTest
    fun tearDown() = stopKoin()

    @Test
    fun graph_resolves_repository_and_now() {
        val koin = startKoin { modules(appModule, inMemoryPersistenceModule()) }.koin

        assertNotNull(koin.get<TaskRepository>(), "TaskRepository must be bound")
        val now = koin.get<Now>()
        assertNotNull(now(), "Now must produce a timestamp")
    }

    @Test
    fun persistence_module_seeds_the_repository() = kotlinx.coroutines.test.runTest {
        val seed = listOf(Task(id = 1, title = "seeded"))
        val koin = startKoin { modules(appModule, inMemoryPersistenceModule(seed)) }.koin

        assertEquals(listOf("seeded"), koin.get<TaskRepository>().all().map { it.title })
    }
}
