package dev.kmpilot.todo.data

import dev.kmpilot.todo.domain.Task
import kotlinx.coroutines.flow.Flow

/**
 * The DDD repository PORT (Ports & Adapters). The app/use-cases depend ONLY on this interface;
 * the backend is a swappable ADAPTER — local SQL (SQLDelight default) or a remote backend
 * (Supabase / PocketBase / Firebase) — chosen via DI. This single seam is the BYOK moat:
 * flipping the backend is a one-line binding change, the rest of the app never knows.
 */
interface TaskRepository {
    /** Reactive stream of all tasks (the kmp-conventions StateFlow contract). */
    fun observeAll(): Flow<List<Task>>
    suspend fun all(): List<Task>
    suspend fun byId(id: Long): Task?
    /** id <= 0 inserts (and assigns an id); otherwise updates. Returns the persisted task. */
    suspend fun upsert(task: Task): Task
    suspend fun delete(id: Long)

    /**
     * Keyset-paginated read, ordered by id. **Default** = client-side over [all] — correct for every adapter,
     * so in-memory/remote get paging for free. SQL adapters **override** this to push the window down to the DB
     * (`LIMIT` + `WHERE id > cursor`). The conformance test proves both paths behave identically.
     */
    suspend fun page(request: PageRequest): Page<Task> {
        val after = request.afterId
        val ordered = all().asSequence()
            .filter { after == null || it.id > after }
            .sortedBy { it.id }
            .toList()
        val window = ordered.take(request.limit)
        return Page(items = window, nextCursor = window.lastOrNull()?.id, hasMore = ordered.size > request.limit)
    }
}
