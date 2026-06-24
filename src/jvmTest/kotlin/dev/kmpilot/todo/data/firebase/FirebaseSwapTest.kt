package dev.kmpilot.todo.data.firebase

import dev.kmpilot.todo.auth.Session
import dev.kmpilot.todo.data.ScopedTaskRepository
import dev.kmpilot.todo.data.TaskRepository
import dev.kmpilot.todo.domain.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * THE FIREBASE BACKEND-SWAP PROOF — the SAME multi-user isolation as [dev.kmpilot.todo.auth.MultiUserContract] and
 * [dev.kmpilot.todo.data.supabase.SupabaseSwapTest], now over a REAL running Firebase Emulator Suite (Auth
 * 127.0.0.1:9099, Firestore 127.0.0.1:8080, project `demo-todo`), with ownership enforced SERVER-SIDE by Firestore
 * security RULES instead of the on-device [ScopedTaskRepository] decorator.
 *
 * The thesis holds across a SECOND backend: the app's ports never change; only the adapters do. The on-device
 * ownership decorator is gone — [FirebaseTaskRepository] constrains its queries to `ownerId == uid` (it MUST, because
 * Firestore VALIDATES queries rather than auto-filtering — see that class's docs) and the rules permit only that. The
 * test asserts the isolation holds AND that the SERVER (the rules) is the enforcer: B cannot read/update/delete A's
 * docs even though B knows A's exact document id — the only thing that changed is the signed-in user.
 *
 * Requires the emulators up. A reachability guard skips the live proof when Firestore (8080) is down. Unique emails
 * per run avoid collisions across reruns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseSwapTest {

    // FIELD-INIT ORDER MATTERS: the SDK must be stood up BEFORE the adapter fields below, which default to
    // `Firebase.auth` / `Firebase.firestore` (those throw "Default FirebaseApp is not initialized" if accessed first).
    // `bootstrap` runs first and pins Dispatchers.Main: firebase-java-sdk's FirebaseAuth posts its id-token listener
    // onto Dispatchers.Main (Firestore init registers it), but under kotlinx-coroutines-test (shared test classpath)
    // Main is the TestMainDispatcher, which throws until a main is set. tearDown resets it so this process-global
    // override does NOT leak into sibling tests in the same JVM fork (notably the Supabase Realtime test).
    private val bootstrap = run {
        Dispatchers.setMain(Dispatchers.Default)
        FirebaseBootstrap.ensure() // process-global SDK init + point Auth/Firestore at the emulators (idempotent)
    }

    private val auth = FirebaseAuthAdapter()
    private val repo: TaskRepository = FirebaseTaskRepository()

    private val run = "${System.nanoTime()}-${(0..9999).random()}"
    private fun email(who: String) = "swap_${who}_$run@example.com"
    private val password = "password123"

    @BeforeTest fun setUp() {
        // Re-pin Main for THIS method (tearDown reset it after the previous one). Idempotent; ensure() already ran.
        Dispatchers.setMain(Dispatchers.Default)
    }

    @AfterTest fun tearDown() {
        runCatching { auth.signOut() }
        Dispatchers.resetMain() // un-leak the Main override so sibling tests in this JVM see the normal test main
    }

    /** The [session] flow is mapped from authStateChanged via stateIn — await it reflecting the expected user. */
    private suspend fun awaitSession(forEmail: String): Session = withTimeout(15_000) {
        auth.session.first { it != null && it.email.equals(forEmail, ignoreCase = true) }!!
    }
    private suspend fun awaitSignedOut() = withTimeout(15_000) { auth.session.first { it == null } }

    /** Reachability probe so `jvmTest` stays green when the local Firebase emulators aren't running (CI / no Java SDK). */
    private fun firestoreUp(): Boolean = runCatching {
        java.net.Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", 8080), 500); true }
    }.getOrDefault(false)

    @Test
    fun the_firebase_repo_is_plain_crud_not_the_ownership_decorator() {
        // Structural proof of the contrast: the swapped-in adapter is NOT a ScopedTaskRepository — ownership is not a
        // client-side decorator anymore. It moved to the server (Firestore rules). The behavioral proof is below.
        assertEquals(
            FirebaseTaskRepository::class, repo::class,
            "the backend swap binds the real Firestore adapter",
        )
        assertFalse(
            ScopedTaskRepository::class.isInstance(repo),
            "ownership is NOT enforced by the on-device decorator — Firestore security rules do it server-side",
        )
    }

    @Test
    fun multi_user_isolation_is_enforced_by_firestore_rules() = runBlocking {
        if (!firestoreUp()) {
            println("[FirebaseSwapTest] Firebase emulator (127.0.0.1:8080) not running — skipping the live rules proof")
            return@runBlocking
        }
        withTimeout(90_000) {
            // ---- A signs up and adds two tasks ----
            assertTrue(auth.signUp(email("a"), password, "Ann").isSuccess, "A signUp must succeed against Firebase")
            val a = awaitSession(email("a"))
            val a1 = repo.upsert(Task(id = 0, title = "A buy milk"))
            assertTrue(a1.id > 0, "the adapter minted a positive numeric id")
            assertEquals(a.userId, a1.ownerId, "insert stamped ownerId = the signed-in uid (request.auth.uid)")
            repo.upsert(Task(id = 0, title = "A walk dog"))

            // A's owner-scoped query returns EXACTLY A's two rows (the query mirrors the rule, so it is permitted).
            assertEquals(setOf("A buy milk", "A walk dog"), repo.all().map { it.title }.toSet())
            assertEquals("A buy milk", repo.byId(a1.id)?.title)

            // ---- A signs out, B signs up ----
            auth.signOut(); awaitSignedOut()
            assertTrue(auth.signUp(email("b"), password, "Bob").isSuccess, "B signUp must succeed")
            val b = awaitSession(email("b"))
            assertTrue(b.userId != a.userId, "distinct users")

            // *** THE KEY RULES ASSERTION *** — B's owner-scoped all() is EMPTY. Same code, same collection; only the
            // signed-in user (request.auth.uid) changed. So the empty result is the SERVER (Firestore rules) scoping,
            // not our code — the on-device decorator is gone. The rule only permits a query constrained to ownerId==uid.
            assertTrue(repo.all().isEmpty(), "rules: B's owner-scoped query returns none of A's docs")

            // B knows A's exact document id, but STILL cannot read it (the rule denies the cross-owner read → null),
            // and cannot delete it (the rule denies the delete). This is the server enforcing ownership, not the client.
            assertNull(repo.byId(a1.id), "rules: A's doc is unreadable by B even by its exact id")
            repo.delete(a1.id) // denied by the rule → A's doc must survive (asserted after we return to A)

            // B adds their own task; only B's task is visible to B.
            repo.upsert(Task(id = 0, title = "B email boss"))
            assertEquals(listOf("B email boss"), repo.all().map { it.title })

            // ---- Back to A: A still sees exactly their two intact tasks ----
            auth.signOut(); awaitSignedOut()
            assertTrue(auth.signIn(email("a"), password).isSuccess, "A signs back in")
            awaitSession(email("a"))
            assertEquals(setOf("A buy milk", "A walk dog"), repo.all().map { it.title }.toSet())
            assertEquals("A buy milk", repo.byId(a1.id)?.title, "A's doc survived B's delete attempt (rule denied it)")
            assertFalse(repo.all().any { it.title == "B email boss" }, "A never sees B's doc")

            // ---- cleanup: A removes A's own docs so reruns stay tidy (the rule lets A delete only A's own) ----
            repo.all().forEach { repo.delete(it.id) }
            assertTrue(repo.all().isEmpty(), "A cleaned up their own docs")
        }
    }
}
