package dev.kmpilot.todo.data

import dev.kmpilot.todo.domain.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A repository that always fails — the test/preview fixture for the "backend error" precondition. Lets a
 * scenario boot the app straight into the Error/Retry state (which a healthy in-memory repo can never reach),
 * so it can be eyeballed in the frame and asserted by the model-based test.
 */
class FailingTaskRepository(
    private val reason: String = "Simulated backend failure",
) : TaskRepository {
    override fun observeAll(): Flow<List<Task>> = flow { throw RuntimeException(reason) }
    override suspend fun all(): List<Task> = throw RuntimeException(reason)
    override suspend fun byId(id: Long): Task? = throw RuntimeException(reason)
    override suspend fun upsert(task: Task): Task = throw RuntimeException(reason)
    override suspend fun delete(id: Long) = throw RuntimeException(reason)
}
