package dev.kmpilot.todo.data.supabase

import dev.kmpilot.todo.auth.Session
import dev.kmpilot.todo.data.ScopedTaskRepository
import dev.kmpilot.todo.data.TaskRepository
import dev.kmpilot.todo.domain.Task
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * THE BACKEND-SWAP PROOF — the SAME multi-user isolation as [dev.kmpilot.todo.auth.MultiUserContract], but now over a
 * REAL running Supabase (Docker, 127.0.0.1:54321), with ownership enforced SERVER-SIDE by Postgres Row-Level Security
 * instead of the on-device [ScopedTaskRepository] decorator.
 *
 * The thesis (`docs/backend/embeddable-vs-server.md`): defer the backend, then swap it thinly. The app's ports never
 * change; only the adapters do. Here the local-RLS decorator is gone entirely — [SupabaseTaskRepository] is plain CRUD
 * (no `owner_id` filtering anywhere in our code), and the database's RLS policies (`owner_id = auth.uid()`) do all the
 * scoping. The test asserts the isolation holds AND that the server is the one enforcing it (B's `all()` is empty even
 * though our repo issues an unfiltered `SELECT *`; only RLS can explain that).
 *
 * Requires the local stack up. Unique emails per run avoid collisions across reruns.
 */
class SupabaseSwapTest {

    private val apiUrl = "http://127.0.0.1:54321"
    private val anonKey =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0"

    private val client = SupabaseClientFactory.create(apiUrl, anonKey)
    private val auth = SupabaseAuthAdapter(client)
    private val repo: TaskRepository = SupabaseTaskRepository(client)

    private val run = "${System.nanoTime()}-${(0..9999).random()}"
    private fun email(who: String) = "swap_${who}_$run@example.com"
    private val password = "password123"

    @AfterTest fun tearDown() {
        runCatching { auth.signOut() }
    }

    /** The [session] flow is mapped from GoTrue's sessionStatus via stateIn — await it reflecting the expected user. */
    private suspend fun awaitSession(forEmail: String): Session = withTimeout(10_000) {
        auth.session.first { it != null && it.email.equals(forEmail, ignoreCase = true) }!!
    }
    private suspend fun awaitSignedOut() = withTimeout(10_000) { auth.session.first { it == null } }

    /** Reachability probe so `jvmTest` stays green when the local Supabase stack isn't running (CI / no Docker). */
    private fun supabaseUp(): Boolean = runCatching {
        java.net.Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", 54321), 500); true }
    }.getOrDefault(false)

    @Test
    fun the_supabase_repo_is_plain_crud_not_the_ownership_decorator() {
        // Structural proof of the contrast: the swapped-in adapter is NOT a ScopedTaskRepository — ownership is not a
        // client-side decorator anymore. It moved to the server (RLS). (The behavioral proof is the isolation test.)
        assertEquals(
            SupabaseTaskRepository::class, repo::class,
            "the backend swap binds the real PostgREST adapter",
        )
        assertFalse(
            ScopedTaskRepository::class.isInstance(repo),
            "ownership is NOT enforced by the on-device decorator — RLS does it server-side",
        )
    }

    @Test
    fun multi_user_isolation_is_enforced_by_postgres_RLS() = runBlocking {
        if (!supabaseUp()) {
            println("[SupabaseSwapTest] local Supabase (127.0.0.1:54321) not running — skipping the live RLS proof")
            return@runBlocking
        }
        withTimeout(60_000) {
            // ---- A signs up and adds two tasks ----
            assertTrue(auth.signUp(email("a"), password, "Ann").isSuccess, "A signUp must succeed against Supabase")
            val a = awaitSession(email("a"))
            val a1 = repo.upsert(Task(id = 0, title = "A buy milk"))
            assertTrue(a1.id > 0, "the DB assigned a real bigint id")
            assertEquals(a.userId, a1.ownerId, "insert DEFAULTed owner_id to auth.uid() server-side (we never set it)")
            repo.upsert(Task(id = 0, title = "A walk dog"))

            // A's unfiltered SELECT * returns EXACTLY A's two rows — RLS already scoped it server-side.
            assertEquals(setOf("A buy milk", "A walk dog"), repo.all().map { it.title }.toSet())
            assertEquals("A buy milk", repo.byId(a1.id)?.title)

            // ---- A signs out, B signs up ----
            auth.signOut(); awaitSignedOut()
            assertTrue(auth.signUp(email("b"), password, "Bob").isSuccess, "B signUp must succeed")
            val b = awaitSession(email("b"))
            assertTrue(b.userId != a.userId, "distinct users")

            // *** THE KEY RLS ASSERTION *** — B's all() is EMPTY. Our repo issued the identical unfiltered SELECT *
            // that returned A's rows moments ago; the only thing that changed is the JWT. So the empty result is
            // PROOF the server (Postgres RLS), not our code, did the scoping. The on-device decorator is gone.
            assertTrue(repo.all().isEmpty(), "RLS: B sees none of A's rows from an unfiltered server query")

            // B cannot read A's row by id (RLS makes it invisible → null), and cannot delete it (matches 0 rows).
            assertNull(repo.byId(a1.id), "RLS: A's row is invisible to B by id")
            repo.delete(a1.id) // silent no-op under RLS (0 rows match for B)

            // B adds their own task; only B's task is visible to B.
            repo.upsert(Task(id = 0, title = "B email boss"))
            assertEquals(listOf("B email boss"), repo.all().map { it.title })

            // ---- Back to A: A still sees exactly their two intact tasks ----
            auth.signOut(); awaitSignedOut()
            assertTrue(auth.signIn(email("a"), password).isSuccess, "A signs back in")
            awaitSession(email("a"))
            assertEquals(setOf("A buy milk", "A walk dog"), repo.all().map { it.title }.toSet())
            assertEquals("A buy milk", repo.byId(a1.id)?.title, "A's row survived B's delete attempt (RLS denied it)")
            assertFalse(repo.all().any { it.title == "B email boss" }, "A never sees B's row")

            // ---- cleanup: remove A's rows so reruns stay tidy (RLS lets A delete only A's own) ----
            repo.all().forEach { repo.delete(it.id) }
            assertTrue(repo.all().isEmpty(), "A cleaned up their own rows")
        }
    }
}
