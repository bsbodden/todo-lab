package dev.kmpilot.todo.data.remote

import dev.kmpilot.todo.data.TaskRepository
import dev.kmpilot.todo.domain.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Remote-backed adapter for the [TaskRepository] port. It is backend-AGNOSTIC: hand it any
 * [BackendClient] (the real Ktor [RestBackendClient], or Supabase/PocketBase/Firebase variants) and it
 * satisfies the exact same contract the local SQLDelight adapter does. This is the BYOK swap — change one
 * binding, not the app.
 *
 * **Proven, not aspirational:** `RemoteTaskRepositoryTest` runs the same `assertRepositoryContract` +
 * `assertPagingContract` against a `RestBackendClient` over a MockEngine backend — real JSON-on-HTTP. The
 * local cache mirrors the "server-authoritative + local cache" default topology (see docs/research/10).
 */
class RemoteTaskRepository(private val client: BackendClient) : TaskRepository {
    private val cache = MutableStateFlow<List<Task>>(emptyList())

    override fun observeAll(): Flow<List<Task>> = cache.asStateFlow()

    override suspend fun all(): List<Task> =
        client.list().map { it.toDomain() }.also { cache.value = it }

    override suspend fun byId(id: Long): Task? = client.get(id)?.toDomain()

    override suspend fun upsert(task: Task): Task {
        val saved = client.upsert(task.toDto()).toDomain()
        all() // refresh the cache/stream
        return saved
    }

    override suspend fun delete(id: Long) {
        client.delete(id); all()
    }
}
