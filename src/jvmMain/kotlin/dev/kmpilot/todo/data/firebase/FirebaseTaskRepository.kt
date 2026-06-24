package dev.kmpilot.todo.data.firebase

import dev.kmpilot.todo.data.TaskRepository
import dev.kmpilot.todo.domain.AlarmInterval
import dev.kmpilot.todo.domain.Task
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import dev.gitlive.firebase.firestore.code
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * The wire DTO for a `tasks/{docId}` Firestore document.
 *
 * `ownerId` is the field the security rule keys on (`resource.data.ownerId == request.auth.uid`) — the Firestore
 * analogue of Supabase's `owner_id` RLS column. Unlike Postgres (a bigint identity column the DB assigns), Firestore
 * has no server-side numeric identity, so we carry our own [numericId] field to satisfy the domain's `Task.id: Long`
 * contract; it doubles as the document path (see [FirebaseTaskRepository]). All `@Serializable` so the GitLive SDK can
 * encode/decode it directly.
 */
@Serializable
data class TaskDoc(
    @SerialName("numericId") val numericId: Long,
    @SerialName("ownerId") val ownerId: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String? = null,
    @SerialName("categoryId") val categoryId: Long? = null,
    @SerialName("dueDate") val dueDate: String? = null,
    @SerialName("creationDate") val creationDate: String? = null,
    @SerialName("completedDate") val completedDate: String? = null,
    @SerialName("isCompleted") val isCompleted: Boolean = false,
    @SerialName("isRepeating") val isRepeating: Boolean = false,
    @SerialName("alarmInterval") val alarmInterval: String? = null,
) {
    fun toDomain(): Task = Task(
        id = numericId,
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
        fun fromDomain(task: Task, numericId: Long, ownerId: String): TaskDoc = TaskDoc(
            numericId = numericId,
            ownerId = ownerId,
            title = task.title,
            description = task.description,
            categoryId = task.categoryId,
            dueDate = task.dueDate?.let(::formatTs),
            creationDate = task.creationDate?.let(::formatTs),
            completedDate = task.completedDate?.let(::formatTs),
            isCompleted = task.isCompleted,
            isRepeating = task.isRepeating,
            alarmInterval = task.alarmInterval?.name,
        )

        private fun formatTs(dt: LocalDateTime): String = dt.toString()
        private fun parseTs(raw: String): LocalDateTime? = runCatching {
            LocalDateTime.parse(raw.substringBefore('+').substringBefore('Z').trim().replace(' ', 'T'))
        }.getOrNull()
    }
}

/**
 * The REAL [TaskRepository] adapter over GitLive's **Firestore** — the data half of the Firebase backend SWAP, and the
 * structural twin of [dev.kmpilot.todo.data.supabase.SupabaseTaskRepository]. Same port, same isolation guarantee,
 * but a DIFFERENT enforcement mechanism that is worth stating precisely:
 *
 * **Firestore rules VALIDATE a query; Postgres RLS AUTO-FILTERS a query.**
 *  - Supabase: the repo issues an *unfiltered* `SELECT *` and Postgres RLS silently scopes the rows to `auth.uid()`.
 *    Forgetting a `where` is harmless — you just get your own rows.
 *  - Firestore: the rules engine evaluates `allow read: if resource.data.ownerId == request.auth.uid` against the
 *    QUERY, not row-by-row over the result. An *unconstrained* `collection("tasks").get()` is rejected WHOLESALE with
 *    PERMISSION_DENIED — the engine can't prove every doc the query *could* return is owned by the caller. It does NOT
 *    drop disallowed docs; the whole read throws. So the client MUST mirror the rule: `where { "ownerId" equalTo uid }`.
 *
 * Consequence for this adapter: every read is owner-constrained ([all], [observeAll], [byId] via an owner-scoped doc),
 * and every write stamps `ownerId = uid` so the doc satisfies the `allow create/update` rule. There is no on-device
 * ownership decorator ([dev.kmpilot.todo.data.ScopedTaskRepository]) — the server (the rules) is the enforcer, exactly
 * as with Supabase RLS. The `uid` comes from the signed-in [FirebaseAuth] user, so a signed-out caller gets empty
 * reads and denied writes, matching the [dev.kmpilot.todo.auth.MultiUserContract].
 *
 * Numeric ids: Firestore has no bigint identity, so we mint a client-side numeric id and use its decimal string as the
 * document path — that lets `byId(Long)`/`delete(Long)` address a doc directly and keeps the domain's `Task.id: Long`.
 */
class FirebaseTaskRepository(
    private val db: FirebaseFirestore = Firebase.firestore,
    private val auth: FirebaseAuth = Firebase.auth,
) : TaskRepository {

    private val tasks get() = db.collection(COLLECTION)

    /** The signed-in uid, or null when signed out (reads → empty, writes → denied), matching the scaffold's contract. */
    private val uid: String? get() = auth.currentUser?.uid

    override suspend fun all(): List<Task> {
        val owner = uid ?: return emptyList()
        // Owner-constrained query — MUST mirror the rule (Firestore validates, it does not auto-filter). An
        // unconstrained get() would throw PERMISSION_DENIED, not return a filtered subset.
        return tasks.where { "ownerId" equalTo owner }
            .get().documents
            .map { it.data<TaskDoc>().toDomain() }
            .sortedBy { it.id }
    }

    override suspend fun byId(id: Long): Task? {
        val owner = uid ?: return null
        // Firestore semantics that MUST be handled here (this is a real difference from Supabase's null-returning
        // single-row select): a `get()` of a document the read rule DENIES — a non-existent doc, OR another user's
        // doc — does NOT return an empty snapshot; it THROWS PERMISSION_DENIED. So a denied/missing get is the
        // silent-miss the scaffold's decorator gives: return null. (Postgres RLS instead just yields zero rows.)
        val snap = runCatching { tasks.document(id.toString()).get() }
            .getOrElse { if (it.isPermissionDenied()) return null else throw it }
        if (!snap.exists) return null
        val doc = snap.data<TaskDoc>()
        // Defense in depth: only surface the doc if it's ours (the rule already guarantees this).
        return if (doc.ownerId == owner) doc.toDomain() else null
    }

    override suspend fun upsert(task: Task): Task {
        val owner = uid ?: throw IllegalStateException("Cannot save a task while signed out")
        val numericId = if (task.id > 0L) task.id else mintId()
        val doc = TaskDoc.fromDomain(task, numericId = numericId, ownerId = owner)
        // set() with the numericId as the document path: create on insert, overwrite on update. ownerId is stamped
        // server-checkable so the `allow create/update: ownerId == request.auth.uid` rule passes.
        tasks.document(numericId.toString()).set(doc)
        return doc.toDomain()
    }

    override suspend fun delete(id: Long) {
        uid ?: return
        // The rule scopes the delete to our docs; deleting another user's doc is denied by the rule. We address the
        // doc by its numeric-id path. (A missing/foreign doc → no-op / denied, matching the silent-no-op contract.)
        runCatching { tasks.document(id.toString()).delete() }
    }

    /**
     * REAL cross-client realtime via Firestore snapshot listeners (the GitLive `.snapshots` Flow). The owner-scoped
     * query's `snapshots` re-emits the full owner's list on every server-side change — the Firestore analogue of
     * Supabase Realtime's Postgres-Changes stream. When signed out we emit a single empty list (no listener to scope).
     * `catch` keeps a transient listener error from crashing the collector (e.g. a race during sign-out).
     */
    override fun observeAll(): Flow<List<Task>> {
        val owner = uid ?: return flow { emit(emptyList()) }
        return tasks.where { "ownerId" equalTo owner }
            .snapshots
            .map { snap -> snap.documents.map { it.data<TaskDoc>().toDomain() }.sortedBy { it.id } }
            .catch { emit(emptyList()) }
    }

    /** True when a Firestore call was rejected by the security rules (a denied/missing read → treat as a miss). */
    private fun Throwable.isPermissionDenied(): Boolean =
        this is FirebaseFirestoreException && code == FirestoreExceptionCode.PERMISSION_DENIED

    private fun mintId(): Long {
        // A positive, effectively-unique numeric id (Firestore has no identity column). Time-based high bits + random
        // low bits keep collisions vanishingly unlikely across a test run without needing a server round-trip.
        val millis = System.currentTimeMillis() and 0x1FFFFFFFFFF // ~41 bits of time
        val rand = Random.nextInt(0, 1 shl 20).toLong()           // 20 bits of entropy
        return (millis shl 20) or rand
    }

    companion object {
        const val COLLECTION: String = "tasks"
    }
}
