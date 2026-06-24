package dev.kmpilot.todo.data

import dev.kmpilot.todo.auth.AuthPort
import dev.kmpilot.todo.domain.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The local-RLS decorator — the "your backend" OWNERSHIP seam, enforced on-device.
 *
 * It wraps any [TaskRepository] [delegate] and scopes every operation to the current [auth] session's user:
 *  - **reads** are filtered to `ownerId == session.userId` (and empty when signed out),
 *  - **writes** stamp `ownerId = currentUserId` and deny touching rows owned by someone else.
 *
 * This is the faithful on-device behavior of access rules (`docs/backend/embeddable-vs-server.md` §2: "Access
 * rules / RLS behavior → a decorator evaluating the policy against the local session"). A real backend would
 * enforce the SAME rule server-side via Postgres RLS / Firestore security rules, GENERATED from the domain — the
 * deferred maturity-time piece. The rule lives here once; only where it runs changes.
 */
class ScopedTaskRepository(
    private val delegate: TaskRepository,
    private val auth: AuthPort,
) : TaskRepository {

    private val currentUserId: String? get() = auth.session.value?.userId

    private fun List<Task>.ownedBy(userId: String?): List<Task> =
        if (userId == null) emptyList() else filter { it.ownerId == userId }

    override fun observeAll(): Flow<List<Task>> =
        combine(delegate.observeAll(), auth.session) { tasks, session ->
            tasks.ownedBy(session?.userId)
        }

    override suspend fun all(): List<Task> = delegate.all().ownedBy(currentUserId)

    override suspend fun byId(id: Long): Task? =
        delegate.byId(id)?.takeIf { it.ownerId == currentUserId && currentUserId != null }

    override suspend fun upsert(task: Task): Task {
        val owner = currentUserId ?: throw IllegalStateException("Cannot save a task while signed out")
        if (task.id > 0L) {
            // Updating an existing row: it must belong to the current user (deny cross-tenant writes).
            val existing = delegate.byId(task.id)
            if (existing != null && existing.ownerId != owner) {
                throw IllegalStateException("Task ${task.id} is owned by another user")
            }
        }
        return delegate.upsert(task.copy(ownerId = owner))
    }

    override suspend fun delete(id: Long) {
        // Only delete if the target row is owned by the current user; otherwise a no-op (deny silently).
        val existing = delegate.byId(id)
        if (existing != null && existing.ownerId == currentUserId && currentUserId != null) {
            delegate.delete(id)
        }
    }

    // page() inherits the port default, which is built on all() — so it is already scoped.
}
