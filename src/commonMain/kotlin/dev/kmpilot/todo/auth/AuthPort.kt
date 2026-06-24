package dev.kmpilot.todo.auth

import kotlinx.coroutines.flow.StateFlow

/** The signed-in identity. The minimum the app/RLS needs: a stable [userId] + display fields. */
data class Session(
    val userId: String,
    val email: String,
    val displayName: String,
)

/**
 * The identity PORT (Ports & Adapters) — the third seam from the backend model (`docs/backend/embeddable-vs-server.md`).
 *
 * Two kinds of sign-in live behind it:
 *  - email/password ([signUp] / [signIn]) is **embeddable** — faithfully on-device (a local users table + hashed pw).
 *  - [signInWith] is the **consumed-SERVICE** stand-in — "Sign in with Google/Apple/Okta" runs against the provider,
 *    not a backend you host. It crosses only the external-party boundary, so it adds NO backend decision.
 *
 * The app depends only on this interface; the local scaffold ([LocalAuthScaffold]) is the dev adapter. A real
 * multi-user backend (token issuance, identity validation for YOUR resources) is the deferred "your backend" seam.
 */
interface AuthPort {
    /** The current session, or null when signed out. Drives the app's login gate + the RLS scope. */
    val session: StateFlow<Session?>

    suspend fun signUp(email: String, password: String, displayName: String): Result<Session>
    suspend fun signIn(email: String, password: String): Result<Session>
    fun signOut()

    /**
     * The OAuth/OIDC/SSO **service** stand-in (a consumed service, NOT your backend). The scaffold fakes it:
     * create/return a local user named after the provider. The real thing is a keyed CLIENT integration
     * (Google/Apple sign-in SDK + your client id/secret), wired anytime keys are supplied — it never becomes a
     * backend of your own. See `docs/backend/embeddable-vs-server.md` §1.
     */
    suspend fun signInWith(provider: String): Result<Session>
}
