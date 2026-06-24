package dev.kmpilot.todo.data.supabase

import dev.kmpilot.todo.auth.AuthPort
import dev.kmpilot.todo.auth.Session
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive

/**
 * The REAL [AuthPort] adapter over supabase-kt's GoTrue ([io.github.jan.supabase.auth.Auth]).
 *
 * This is the identity half of the backend SWAP: the app keeps depending only on [AuthPort]; the local
 * [dev.kmpilot.todo.auth.LocalAuthScaffold] (a local users table + hashed pw) is replaced by a server that issues +
 * validates real JWTs. The mapping is deliberately thin — that is the thesis (`docs/backend/embeddable-vs-server.md`):
 *  - [signUp]   → `auth.signUpWith(Email) { email; password }`  (instant login here — no email confirmation locally)
 *  - [signIn]   → `auth.signInWith(Email) { email; password }`
 *  - [signOut]  → `auth.signOut()`
 *  - [session]  → maps `auth.sessionStatus`: [SessionStatus.Authenticated] → a domain [Session], everything else → null.
 *
 * The issued JWT (carried by the same client) is what PostgREST presents to Postgres as `auth.uid()`, so
 * [SupabaseTaskRepository] needs NO ownership logic — RLS scopes every row to this user server-side. That is the
 * contrast with [dev.kmpilot.todo.data.ScopedTaskRepository], which evaluates the same rule on-device.
 */
class SupabaseAuthAdapter(
    private val client: SupabaseClient,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : AuthPort {

    private val auth get() = client.auth

    override val session: StateFlow<Session?> =
        auth.sessionStatus
            .map { status -> (status as? SessionStatus.Authenticated)?.session?.user?.toSession() }
            .stateIn(scope, SharingStarted.Eagerly, currentSession())

    private fun currentSession(): Session? =
        (auth.sessionStatus.value as? SessionStatus.Authenticated)?.session?.user?.toSession()

    override suspend fun signUp(email: String, password: String, displayName: String): Result<Session> =
        runCatching {
            auth.signUpWith(Email) {
                this.email = email.trim()
                this.password = password
            }
            // Email signup is instant locally (no confirmation) → the client now holds an authenticated session.
            requireSession()
        }

    override suspend fun signIn(email: String, password: String): Result<Session> =
        runCatching {
            auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
            }
            requireSession()
        }

    override fun signOut() {
        // The port's signOut is synchronous; the GoTrue call is suspend, so bridge it. Tests run single-threaded.
        runBlocking { auth.signOut() }
    }

    /** OAuth is a separate (browser/deeplink) flow — out of scope for this server-CRUD proof. */
    override suspend fun signInWith(provider: String): Result<Session> =
        Result.failure(NotImplementedError("OAuth ($provider) is a separate consumed-service flow, not part of this adapter"))

    private fun requireSession(): Session =
        currentSession() ?: error("Supabase returned no authenticated session after auth")

    /** Map a GoTrue [UserInfo] to the app's [Session]. displayName falls back to the email when no metadata is set. */
    private fun UserInfo.toSession(): Session {
        val mail = email ?: ""
        val name = userMetadata?.get("display_name")?.jsonPrimitive?.content
            ?: userMetadata?.get("full_name")?.jsonPrimitive?.content
            ?: mail
        return Session(userId = id, email = mail, displayName = name)
    }
}
