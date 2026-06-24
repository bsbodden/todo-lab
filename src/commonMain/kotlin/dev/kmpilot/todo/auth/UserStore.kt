package dev.kmpilot.todo.auth

/**
 * A persisted account row — exactly what [LocalAuthScaffold] needs to authenticate: a stable [id], the display
 * fields, and the hashed-password material ([passwordHash] + [salt]). NEVER carries plaintext.
 */
data class StoredUser(
    val id: String,
    val email: String,        // normalized (trimmed + lowercased) — the lookup key + the hashing salt
    val displayName: String,
    val passwordHash: String,
    val salt: String,
)

/**
 * The persistence seam for the local-auth scaffold's users table. [LocalAuthScaffold] depends only on this
 * interface, so the in-memory store (tests/preview) and the SQLDelight store (durable on-device) are
 * interchangeable — the auth port itself never changes.
 */
interface UserStore {
    /** Insert a brand-new account. Caller guarantees the email is free (checked via [findByEmail] first). */
    fun create(user: StoredUser)
    fun findByEmail(email: String): StoredUser?
    fun findById(id: String): StoredUser?
    fun all(): List<StoredUser>
}

/** The default store — a simple in-memory map keyed by normalized email. The original scaffold behavior. */
class InMemoryUserStore : UserStore {
    private val byEmail = mutableMapOf<String, StoredUser>()

    override fun create(user: StoredUser) {
        byEmail[user.email] = user
    }

    override fun findByEmail(email: String): StoredUser? = byEmail[email]
    override fun findById(id: String): StoredUser? = byEmail.values.firstOrNull { it.id == id }
    override fun all(): List<StoredUser> = byEmail.values.toList()
}
