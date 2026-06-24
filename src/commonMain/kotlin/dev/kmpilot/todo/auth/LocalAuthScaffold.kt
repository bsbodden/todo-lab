package dev.kmpilot.todo.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDateTime

/**
 * The LOCAL identity engine — the embeddable [AuthPort] adapter used as DEV SCAFFOLDING.
 *
 * A users table (behind the [UserStore] seam) + a single live [session]. This is the faithful on-device shape of
 * "Auth — single device / simulated multi-user" from `docs/backend/embeddable-vs-server.md` §2: real login behavior
 * (sign up, sign in, bad-creds rejection, sign out, account switching) with ZERO server. The store is pluggable:
 * [InMemoryUserStore] for tests/preview, [dev.kmpilot.todo.data.SqlDelightUserStore] to PERSIST across app restarts
 * on device — the seam (this port) does not change either way.
 *
 * NOT production: a real multi-user backend issues/validates tokens server-side and hashes with bcrypt/argon2.
 *
 * @param store the persistence seam for accounts (default in-memory, so existing tests/`MultiUserContract` are unchanged).
 * @param now a clock seam (unused by the store today; kept so a SQL swap can stamp createdAt).
 */
class LocalAuthScaffold(
    @Suppress("unused") private val now: () -> LocalDateTime,
    private val store: UserStore = InMemoryUserStore(),
) : AuthPort {

    private val _session = MutableStateFlow<Session?>(null)
    override val session: StateFlow<Session?> = _session.asStateFlow()

    // User ids stay the deterministic "u1", "u2", … shape (the wasm preview's demo user relies on u1). Seed the
    // counter past any rows already present, so reopening a persisted store keeps minting fresh, non-colliding ids.
    private var nextUserId = (store.all().mapNotNull { it.id.removePrefix("u").toIntOrNull() }.maxOrNull() ?: 0) + 1

    override suspend fun signUp(email: String, password: String, displayName: String): Result<Session> {
        val key = email.trim().lowercase()
        if (key.isBlank() || password.isBlank() || displayName.isBlank()) {
            return Result.failure(IllegalArgumentException("Email, password and name are required"))
        }
        if (store.findByEmail(key) != null) {
            return Result.failure(IllegalStateException("An account with that email already exists"))
        }
        val user = StoredUser(
            id = "u${nextUserId++}",
            email = key,
            displayName = displayName.trim(),
            passwordHash = ScaffoldPasswordHash.hash(password, salt = key), // never store plaintext
            salt = key,
        )
        store.create(user)
        return Result.success(user.toSession(email.trim()).also { _session.value = it })
    }

    override suspend fun signIn(email: String, password: String): Result<Session> {
        val key = email.trim().lowercase()
        val user = store.findByEmail(key)
            ?: return Result.failure(IllegalArgumentException("No account for that email"))
        if (!ScaffoldPasswordHash.verify(password, salt = user.salt, expected = user.passwordHash)) {
            return Result.failure(IllegalArgumentException("Incorrect password"))
        }
        return Result.success(user.toSession(email.trim()).also { _session.value = it })
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
        val user = store.findByEmail(key) ?: StoredUser(
            id = "u${nextUserId++}",
            email = key,
            displayName = "$provider User",
            // No password path exists for OAuth users; store a non-guessable marker, never plaintext.
            passwordHash = ScaffoldPasswordHash.hash("oauth:$provider", salt = key),
            salt = key,
        ).also { store.create(it) }
        return Result.success(user.toSession(email).also { _session.value = it })
    }

    private fun StoredUser.toSession(email: String) = Session(userId = id, email = email, displayName = displayName)
}
