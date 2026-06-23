package dev.kmpilot.todo.data

import dev.kmpilot.todo.domain.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** In-memory adapter — the default for tests, previews, and the "simulate accumulated state" fixture. */
class InMemoryTaskRepository(seed: List<Task> = emptyList()) : TaskRepository {
    private val state = MutableStateFlow(seed)
    private val mutex = Mutex()
    private var nextId: Long = (seed.maxOfOrNull { it.id } ?: 0L) + 1L

    override fun observeAll(): Flow<List<Task>> = state.asStateFlow()
    override suspend fun all(): List<Task> = state.value
    override suspend fun byId(id: Long): Task? = state.value.find { it.id == id }

    override suspend fun upsert(task: Task): Task = mutex.withLock {
        if (task.id <= 0L) {
            val created = task.copy(id = nextId++)
            state.value = state.value + created
            created
        } else {
            val exists = state.value.any { it.id == task.id }
            state.value = if (exists) state.value.map { if (it.id == task.id) task else it }
            else state.value + task
            task
        }
    }

    override suspend fun delete(id: Long) = mutex.withLock {
        state.value = state.value.filterNot { it.id == id }
    }
}
