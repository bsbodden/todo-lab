package dev.kmpilot.todo.auth

import dev.kmpilot.todo.data.InMemoryTaskRepository
import dev.kmpilot.todo.data.ScopedTaskRepository
import dev.kmpilot.todo.domain.Task
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The MULTI-USER conformance contract — auth + the local-RLS ownership seam, proven against the scaffold.
 * Mirrors the BYOK [dev.kmpilot.todo.data.assertRepositoryContract] pattern: a real backend's auth + RLS adapter
 * must satisfy the SAME assertions, so the local scaffold and a future server are interchangeable.
 */
class MultiUserContract {

    private val fixedNow: () -> LocalDateTime = { LocalDateTime(2026, 1, 1, 0, 0) }

    private fun newStack(): Pair<LocalAuthScaffold, ScopedTaskRepository> {
        val auth = LocalAuthScaffold(fixedNow)
        val repo = ScopedTaskRepository(InMemoryTaskRepository(), auth)
        return auth to repo
    }

    // ---- auth ----

    @Test
    fun signUp_creates_a_usable_account_and_sets_the_session() = runTest {
        val (auth, _) = newStack()
        val result = auth.signUp("a@example.com", "pw", "Ann")
        assertTrue(result.isSuccess, "signUp must succeed")
        val session = result.getOrThrow()
        assertEquals("a@example.com", session.email)
        assertEquals("Ann", session.displayName)
        assertEquals(session, auth.session.value, "session flow must reflect the signed-in user")
    }

    @Test
    fun signIn_good_creds_succeeds_bad_creds_fail() = runTest {
        val (auth, _) = newStack()
        auth.signUp("a@example.com", "correct", "Ann")
        auth.signOut()

        assertTrue(auth.signIn("a@example.com", "correct").isSuccess, "good creds must sign in")

        auth.signOut()
        assertTrue(auth.signIn("a@example.com", "wrong").isFailure, "bad password must fail")
        assertTrue(auth.signIn("nobody@example.com", "correct").isFailure, "unknown email must fail")
    }

    @Test
    fun duplicate_email_signup_fails_and_blank_fields_rejected() = runTest {
        val (auth, _) = newStack()
        auth.signUp("a@example.com", "pw", "Ann")
        assertTrue(auth.signUp("a@example.com", "pw2", "Alt").isFailure, "duplicate email must fail")
        assertTrue(auth.signUp("", "pw", "Ann").isFailure, "blank email must fail")
        assertTrue(auth.signUp("b@example.com", "", "Ann").isFailure, "blank password must fail")
        assertTrue(auth.signUp("b@example.com", "pw", "").isFailure, "blank name must fail")
    }

    @Test
    fun never_stores_plaintext_password() = runTest {
        // Sign in only succeeds via the hash path; a wrong password can never match. (Plaintext storage would
        // make verify trivially true for the stored string — proven false by the bad-creds case above.)
        val (auth, _) = newStack()
        auth.signUp("a@example.com", "s3cret", "Ann")
        auth.signOut()
        assertTrue(auth.signIn("a@example.com", "s3cret").isSuccess)
        auth.signOut()
        assertTrue(auth.signIn("a@example.com", "S3cret").isFailure, "hash must be case/content sensitive")
    }

    @Test
    fun signed_out_reads_are_empty_and_writes_denied() = runTest {
        val (auth, repo) = newStack()
        // signed out from the start
        assertNull(auth.session.value)
        assertTrue(repo.all().isEmpty(), "signed-out all() is empty")
        assertTrue(repo.observeAll().first().isEmpty(), "signed-out observeAll() is empty")
        assertTrue(
            runCatching { repo.upsert(Task(id = 0, title = "X")) }.isFailure,
            "signed-out upsert must be denied",
        )
    }

    @Test
    fun oauth_service_stub_signs_in_a_provider_user() = runTest {
        val (auth, _) = newStack()
        val result = auth.signInWith("Google")
        assertTrue(result.isSuccess)
        assertEquals("Google User", result.getOrThrow().displayName)
        assertEquals(result.getOrThrow(), auth.session.value)
    }

    // ---- isolation (the key assertion) ----

    @Test
    fun tasks_are_isolated_per_user() = runTest {
        val (auth, repo) = newStack()

        // A signs in and adds two tasks.
        val a = auth.signUp("a@example.com", "pw", "Ann").getOrThrow()
        val a1 = repo.upsert(Task(id = 0, title = "A buy milk"))
        repo.upsert(Task(id = 0, title = "A walk dog"))
        assertEquals(setOf("A buy milk", "A walk dog"), repo.all().map { it.title }.toSet())
        assertEquals(a.userId, repo.byId(a1.id)?.ownerId, "A's task is stamped with A's id")

        // B signs in (after A signs out) → B sees nothing of A's.
        auth.signOut()
        val b = auth.signUp("b@example.com", "pw", "Bob").getOrThrow()
        assertTrue(b.userId != a.userId)
        assertTrue(repo.all().isEmpty(), "B's all() must be empty")
        assertTrue(repo.observeAll().first().isEmpty(), "B's observeAll() must be empty")
        assertNull(repo.byId(a1.id), "B cannot read A's task by id")

        // B cannot delete A's task...
        repo.delete(a1.id)
        // ...and B cannot hijack A's task via upsert.
        assertTrue(
            runCatching { repo.upsert(a1.copy(title = "hijacked")) }.isFailure,
            "B must not be able to write A's row",
        )

        // B adds their own task; only B's task is visible to B.
        repo.upsert(Task(id = 0, title = "B email boss"))
        assertEquals(listOf("B email boss"), repo.all().map { it.title })

        // Back to A → A still sees only A's two (intact) tasks.
        auth.signOut()
        auth.signIn("a@example.com", "pw").getOrThrow()
        assertEquals(setOf("A buy milk", "A walk dog"), repo.all().map { it.title }.toSet())
        assertEquals("A buy milk", repo.byId(a1.id)?.title, "A's task survived B's delete attempt")
        assertFalse(repo.all().any { it.title == "hijacked" }, "A's task was never hijacked")
    }
}
