package dev.kmpilot.todo.data.supabase

import dev.kmpilot.todo.data.TaskRepository
import dev.kmpilot.todo.domain.AlarmInterval
import dev.kmpilot.todo.domain.Task
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.TimeSource
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The wire DTO for the `public.tasks` row — snake_case to match the Postgres columns.
 *
 * `id` and `owner_id` are NULLABLE and OMITTED on insert: the DB assigns `id` (bigint identity) and DEFAULTS
 * `owner_id` to `auth.uid()` (the JWT subject the client carries). Encoding null for them would clobber those
 * defaults, so `encodeDefaults = false` (PostgREST's default via supabase-kt) drops them from the insert body.
 */
@Serializable
data class TaskRow(
    @SerialName("id") val id: Long? = null,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String? = null,
    @SerialName("category_id") val categoryId: Long? = null,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("creation_date") val creationDate: String? = null,
    @SerialName("completed_date") val completedDate: String? = null,
    @SerialName("is_completed") val isCompleted: Boolean = false,
    @SerialName("is_repeating") val isRepeating: Boolean = false,
    @SerialName("alarm_interval") val alarmInterval: String? = null,
    @SerialName("owner_id") val ownerId: String? = null,
) {
    fun toDomain(): Task = Task(
        id = id ?: 0L,
        title = title,
        description = description,
        categoryId = categoryId,
        dueDate = dueDate?.let(::parseTs),
        creationDate = creationDate?.let(::parseTs),
        completedDate = completedDate?.let(::parseTs),
        isCompleted = isCompleted,
        isRepeating = isRepeating,
        alarmInterval = alarmInterval?.let { runCatching { AlarmInterval.valueOf(it) }.getOrNull() },
        ownerId = ownerId,
    )

    companion object {
        /** Insert shape: drop id + owner_id so Postgres supplies the identity id and the `auth.uid()` default. */
        fun forInsert(task: Task): TaskRow = task.toRow().copy(id = null, ownerId = null)

        /** Update shape: keep the id (filtered on) but never send owner_id (RLS pins it; never let a client move a row). */
        fun forUpdate(task: Task): TaskRow = task.toRow().copy(ownerId = null)

        private fun Task.toRow(): TaskRow = TaskRow(
            id = if (id > 0L) id else null,
            title = title,
            description = description,
            categoryId = categoryId,
            dueDate = dueDate?.let(::formatTs),
            creationDate = creationDate?.let(::formatTs),
            completedDate = completedDate?.let(::formatTs),
            isCompleted = isCompleted,
            isRepeating = isRepeating,
            alarmInterval = alarmInterval?.name,
        )

        // Postgres timestamptz <-> kotlinx LocalDateTime. The DB has no zone offset stored beyond UTC for our use;
        // we round-trip the naive datetime (the domain type is zone-less), trimming any trailing offset/space.
        private fun formatTs(dt: LocalDateTime): String = dt.toString()
        private fun parseTs(raw: String): LocalDateTime? = runCatching {
            LocalDateTime.parse(raw.substringBefore('+').substringBefore('Z').trim().replace(' ', 'T'))
        }.getOrNull()
    }
}

/**
 * The REAL [TaskRepository] adapter over supabase-kt's PostgREST — the data half of the backend SWAP.
 *
 * **This is PLAIN CRUD with NO ownership logic — and that is the whole point.** Contrast with
 * [dev.kmpilot.todo.data.ScopedTaskRepository], which wraps a repo and evaluates `owner_id == session.userId` on
 * every read/write *on-device*. Here there is no such decorator: the JWT the [SupabaseAuthAdapter]'s client carries
 * is presented to Postgres, and the table's Row-Level Security policies (`owner_id = auth.uid()` on select/insert/
 * update/delete) do the scoping SERVER-SIDE. `select()` only ever returns this user's rows; an insert's `owner_id`
 * DEFAULTs to `auth.uid()`; another user's rows are simply invisible (a no-op delete, a null byId). The ownership
 * rule moved from the client decorator to the database — the deferred maturity-time swap the thesis promises.
 *
 * See `docs/backend/embeddable-vs-server.md`: "Access rules / RLS behavior → a decorator evaluating the policy
 * against the local session" (scaffold) becomes "enforced server-side via Postgres RLS, generated from the domain".
 */
class SupabaseTaskRepository(private val client: SupabaseClient) : TaskRepository {

    private val tasks get() = client.postgrest["tasks"]

    // A long-lived scope for cleanup work that must NOT be cancelled with the (already-cancelling) flow collector —
    // namely the suspend `removeChannel` in observeAll's awaitClose. It outlives any single collection.
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Multiplatform monotonic mark for minting unique channel names (replaces the jvm-only System.nanoTime()).
    private val timeSource = TimeSource.Monotonic
    private val started = timeSource.markNow()

    override suspend fun all(): List<Task> =
        tasks.select { order("id", Order.ASCENDING) }
            .decodeList<TaskRow>()
            .map { it.toDomain() }

    override suspend fun byId(id: Long): Task? =
        tasks.select { filter { eq("id", id) } }
            .decodeSingleOrNull<TaskRow>()
            ?.toDomain()

    override suspend fun upsert(task: Task): Task =
        if (task.id <= 0L) {
            // Insert: PostgREST returns the assigned id (and the DEFAULTed owner_id) via `select()`.
            tasks.insert(TaskRow.forInsert(task)) { select() }
                .decodeSingle<TaskRow>()
                .toDomain()
        } else {
            // Update by id; RLS makes this a no-op (zero rows) if the row is not ours. `select()` returns the row.
            tasks.update(TaskRow.forUpdate(task)) {
                select()
                filter { eq("id", task.id) }
            }.decodeSingleOrNull<TaskRow>()?.toDomain()
                ?: task // not ours / not found → unchanged (RLS denied silently, like the scaffold's decorator)
        }

    override suspend fun delete(id: Long) {
        // RLS scopes the DELETE to our rows; deleting someone else's id matches zero rows (a silent no-op).
        tasks.delete { filter { eq("id", id) } }
    }

    /**
     * REAL cross-client realtime — the server-side half of the reactive contract. The local scaffold's
     * [dev.kmpilot.todo.data.InMemoryTaskRepository.observeAll] (a `MutableStateFlow.asStateFlow`) is single-client
     * reactive: it only re-emits when *this* process mutates the state. Cross-client push is the SERVER's job — that's
     * the rendezvous boundary the backend SWAP crosses. Here Supabase Realtime (Postgres-Changes over a WebSocket)
     * delivers another client's writes to us, so two devices signed in as the same user stay in sync with no polling.
     *
     * Pattern (the RLS-correct one): emit the initial RLS-scoped [select] snapshot, then RE-QUERY the whole list on
     * every INSERT/UPDATE/DELETE event and emit the fresh result. Re-querying (rather than applying the delta) keeps us
     * correct under RLS — the change payload alone can drift from the true post-RLS list (UPDATE/DELETE may carry only
     * the PK), but a fresh `select()` is itself RLS-scoped to the caller's JWT. [channelFlow] runs the change collector
     * in a child coroutine; [awaitClose] unsubscribes + disposes the channel on cancel. `distinctUntilChanged` absorbs
     * the tiny initial-snapshot / first-event overlap and any no-op re-emits.
     *
     * Ordering matters: build `postgresChangeFlow` BEFORE `subscribe()` (calling it after the channel has joined throws),
     * and emit the initial snapshot AFTER the JOIN ack (`blockUntilSubscribed = true`) so no event is missed in the join
     * window. With [Auth] installed, the user's access_token rides the socket → the server pushes only this user's rows.
     */
    override fun observeAll(): Flow<List<Task>> = channelFlow {
        // Unique channel name per collection. Multiplatform (no jvm System.nanoTime): a monotonic elapsed-nanos delta
        // plus a random suffix — enough to keep concurrent channels on one client from colliding (a name, not a clock).
        val token = "${started.elapsedNow().inWholeNanoseconds}-${Random.nextInt(0, Int.MAX_VALUE)}"
        val channel = client.channel("tasks-observe-$token")

        // 1) Build the change flow BEFORE subscribing (postgresChangeFlow after JOIN throws IllegalStateException).
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "tasks"
        }

        // 2) Re-query the full RLS-scoped list on every change event and emit it.
        val job = changes
            .onEach { trySend(all()) }
            .launchIn(this)

        // 3) Push the CURRENT JWT onto the Realtime socket BEFORE joining, so the server evaluates RLS as THIS user
        //    (not anon) when deciding which change events to broadcast to us. setAuth() with no token pulls the access
        //    token from the installed Auth plugin's live session — that's the realtime-auth contract. Without it the
        //    socket would join as anon and the owner-private SELECT policy would suppress every event (silent no-op).
        client.realtime.setAuth()

        // Then join, then push the initial snapshot (after the ack so the join window can't drop an event).
        channel.subscribe(blockUntilSubscribed = true)
        trySend(all())

        // 4) Cleanup on cancel — removeChannel is suspend, so run it on the long-lived cleanupScope, not this
        //    (already-cancelling) channelFlow scope, or the unsubscribe would be cancelled before it completes.
        awaitClose {
            job.cancel()
            cleanupScope.launch { client.realtime.removeChannel(channel) }
        }
    }.distinctUntilChanged()
}
