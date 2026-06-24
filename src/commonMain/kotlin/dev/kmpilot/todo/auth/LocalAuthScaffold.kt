package dev.kmpilot.todo.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDateTime

/**
 * The LOCAL identity engine — the embeddable [AuthPort] adapter used as DEV SCAFFOLDING.
 *
 * In-memory only: a users table (email -> account) + a single live [session]. This is the faithful on-device
 * shape of "Auth — single device / simulated multi-user" from `docs/backend/embeddable-vs-server.md` §2: real
 * login behavior (sign up, sign in, bad-creds rejection, sign out, account switching) with ZERO server. The
 * trivial later swap is SQLDelight rows for [users]; the seam (this port) does not change.
 *
 * NOT production: a real multi-user backend issues/validates tokens server-side and hashes with bcrypt/argon2.
 *
 * @param now a clock seam (unused by the in-memory store today; kept so a SQL swap can stamp createdAt).
 */
class LocalAuthScaffold(
    @Suppress("unused") private val now: () -> LocalDateTime,
) : AuthPort {

    private data class Account(val userId: String, val displayName: String, val passwordHash: String)

    private val users = mutableMapOf<String, Account>() // key = normalized email
    private val _session = MutableStateFlow<Session?>(null)
    override val session: StateFlow<Session?> = _session.asStateFlow()

    private var nextUserId = 1

    override suspend fun signUp(email: String, password: String, displayName: String): Result<Session> {
        val key = email.trim().lowercase()
        if (key.isBlank() || password.isBlank() || displayName.isBlank()) {
            return Result.failure(IllegalArgumentException("Email, password and name are required"))
        }
        if (users.containsKey(key)) {
            return Result.failure(IllegalStateException("An account with that email already exists"))
        }
        val account = Account(
            userId = "u${nextUserId++}",
            displayName = displayName.trim(),
            passwordHash = ScaffoldPasswordHash.hash(password, salt = key), // never store plaintext
        )
        users[key] = account
        return Result.success(account.toSession(email.trim()).also { _session.value = it })
    }

    override suspend fun signIn(email: String, password: String): Result<Session> {
        val key = email.trim().lowercase()
        val account = users[key]
            ?: return Result.failure(IllegalArgumentException("No account for that email"))
        if (!ScaffoldPasswordHash.verify(password, salt = key, expected = account.passwordHash)) {
            return Result.failure(IllegalArgumentException("Incorrect password"))
        }
        return Result.success(account.toSession(email.trim()).also { _session.value = it })
    }

    override fun signOut() {
        _session.value = null
    }

    /**
     * Consumed-SERVICE stand-in (see [AuthPort.signInWith]). The real adapter is a keyed client SDK
     * (Google/Apple sign-in) returning a verified identity — not a backend we host. Here we fake the
     * round-trip: a stable local account named after the provider, so multi-user UX is demonstrable offline.
     */
    override suspend fun signInWith(provider: String): Result<Session> {
        val email = "${provider.lowercase()}-user@example.com"
        val key = email.lowercase()
        val account = users.getOrPut(key) {
            Account(
                userId = "u${nextUserId++}",
                displayName = "$provider User",
                // No password path exists for OAuth users; store a non-guessable marker, never plaintext.
                passwordHash = ScaffoldPasswordHash.hash("oauth:$provider", salt = key),
            )
        }
        return Result.success(account.toSession(email).also { _session.value = it })
    }

    private fun Account.toSession(email: String) = Session(userId = userId, email = email, displayName = displayName)
}
