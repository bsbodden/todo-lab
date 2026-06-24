package dev.kmpilot.todo.data.supabase

import dev.kmpilot.todo.auth.Session
import dev.kmpilot.todo.data.TaskRepository
import dev.kmpilot.todo.domain.Task
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * THE CROSS-CLIENT REALTIME PUSH PROOF — the server-side reactive half of the backend SWAP.
 *
 * [dev.kmpilot.todo.data.InMemoryTaskRepository.observeAll] (the local scaffold) is a `MutableStateFlow.asStateFlow`:
 * single-client reactive — it only re-emits when THIS process mutates state. Cross-client push is the SERVER's job;
 * that's the rendezvous boundary the swap crosses. This test proves [SupabaseTaskRepository.observeAll] is REAL
 * realtime, not a poll:
 *
 *   • TWO independent Supabase clients (separate WebSockets/sessions), BOTH signed in as the SAME user A.
 *   • client1 collects `observeAll()` and records every snapshot it sees.
 *   • client2 `upsert`s a brand-new task — client1 NEVER touches the DB.
 *   • Assert client1's flow EMITS a snapshot containing the new task within ~15s.
 *
 * Because client1 made no change of its own, the only way that title can appear in its stream is a change event the
 * Supabase Realtime server pushed over client1's WebSocket. That is realtime (server push), not a local re-read.
 *
 * RLS-on-realtime is exercised implicitly: both clients hold A's JWT, so the server's per-subscriber RLS check
 * (`owner_id = auth.uid()` on the SELECT policy) passes and the INSERT event is delivered. (If we hadn't enabled the
 * table in the `supabase_realtime` publication, no event would arrive and this test would time out — that's the proof
 * the publication change is load-bearing.)
 *
 * Requires the local stack up; a reachability guard skips it otherwise (CI / no Docker), like [SupabaseSwapTest].
 * Determinism notes:
 *  - A UNIQUE title per run (nanoTime) so a rerun's leftover rows can never satisfy the assertion early.
 *  - We start collecting client1's flow and WAIT for its initial snapshot before client2 writes, so the write can only
 *    land as a pushed change event (never as part of the initial select). `subscribe(blockUntilSubscribed=true)` inside
 *    observeAll guarantees the channel has JOINed before that first snapshot, so the write can't slip the join window.
 */
class SupabaseRealtimeTest {

    private val apiUrl = "http://127.0.0.1:54321"
    private val anonKey =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0"

    // TWO independent clients — separate sessions AND separate Realtime WebSockets — both targeting the same backend.
    private val client1 = SupabaseClientFactory.create(apiUrl, anonKey)
    private val client2 = SupabaseClientFactory.create(apiUrl, anonKey)

    private val auth1 = SupabaseAuthAdapter(client1)
    private val auth2 = SupabaseAuthAdapter(client2)
    private val repo1: TaskRepository = SupabaseTaskRepository(client1)
    private val repo2: TaskRepository = SupabaseTaskRepository(client2)

    private val run = "${System.nanoTime()}-${(0..9999).random()}"
    private fun email(who: String) = "rt_${who}_$run@example.com"
    private val password = "password123"

    @AfterTest fun tearDown() {
        runCatching { runBlocking { repo1.all().forEach { repo1.delete(it.id) } } }
        runCatching { auth1.signOut() }
        runCatching { auth2.signOut() }
    }

    private suspend fun awaitSession(auth: SupabaseAuthAdapter, forEmail: String): Session = withTimeout(10_000) {
        auth.session.first { it != null && it.email.equals(forEmail, ignoreCase = true) }!!
    }

    /** Reachability probe so `jvmTest` stays green when the local Supabase stack isn't running (CI / no Docker). */
    private fun supabaseUp(): Boolean = runCatching {
        java.net.Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", 54321), 500); true }
    }.getOrDefault(false)

    @Test
    fun observeAll_pushes_another_clients_write_over_the_websocket() = runBlocking {
        if (!supabaseUp()) {
            println("[SupabaseRealtimeTest] local Supabase (127.0.0.1:54321) not running — skipping the live push proof")
            return@runBlocking
        }
        withTimeout(60_000) {
            // ---- ONE user A, signed in on BOTH clients (so both hold A's JWT → both pass A's RLS) ----
            assertTrue(auth1.signUp(email("a"), password, "Ann").isSuccess, "A signUp on client1 must succeed")
            val a = awaitSession(auth1, email("a"))
            // client2 signs IN as the same A (the account now exists). Distinct session object, same identity.
            assertTrue(auth2.signIn(email("a"), password).isSuccess, "client2 signs in as the same A")
            awaitSession(auth2, email("a"))

            val title = "RT push $run" // unique per run → a stale leftover row can never satisfy the assertion early

            // ---- client1 starts ONE long-lived observeAll() collection on this scope ----
            // The collector signals two things: `ready` on its FIRST emission (the post-JOIN initial snapshot → the
            // WebSocket is subscribed) and `pushed` when a snapshot containing `title` arrives (the cross-client event).
            val ready = CompletableDeferred<Unit>()
            val pushed = CompletableDeferred<List<Task>>()
            val collector = repo1.observeAll()
                .onEach { snapshot ->
                    if (!ready.isCompleted) ready.complete(Unit)
                    if (snapshot.any { it.title == title } && !pushed.isCompleted) pushed.complete(snapshot)
                }
                .launchIn(this)

            // Wait for the channel to be JOINed (observeAll emits its initial snapshot only after the JOIN ack), so the
            // socket is guaranteed live BEFORE client2 writes → the write can ONLY reach client1 as a pushed event.
            withTimeout(15_000) { ready.await() }
            println("[SupabaseRealtimeTest] client1 channel JOINed + initial snapshot received; arming the cross write")

            // ---- client2 (the OTHER connection) inserts the new task. client1 NEVER writes. ----
            val inserted = repo2.upsert(Task(id = 0, title = title))
            assertTrue(inserted.id > 0, "client2's insert got a real id")
            assertTrue(a.userId == inserted.ownerId, "RLS DEFAULTed owner_id to A (same user) — so A's socket may see it")

            // ---- THE PROOF: client1's flow must surface that title within ~15s, purely from a pushed change event ----
            val startNs = System.nanoTime()
            val pushedSnapshot = withTimeout(15_000) { pushed.await() }
            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
            println("[SupabaseRealtimeTest] client1 received client2's insert over the WebSocket in ${elapsedMs}ms")
            assertTrue(
                pushedSnapshot.any { it.title == title },
                "client1's observeAll() received client2's insert over the Realtime WebSocket (server push, not a poll)",
            )
            collector.cancel()
        }
    }
}
