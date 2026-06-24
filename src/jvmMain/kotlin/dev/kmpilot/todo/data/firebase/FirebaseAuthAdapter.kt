package dev.kmpilot.todo.data.firebase

import dev.kmpilot.todo.auth.AuthPort
import dev.kmpilot.todo.auth.Session
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

/**
 * The REAL [AuthPort] adapter over GitLive's Firebase **Auth** — the GoTrue-equivalent identity half of the Firebase
 * backend SWAP, structurally a twin of [dev.kmpilot.todo.data.supabase.SupabaseAuthAdapter].
 *
 * The app keeps depending only on [AuthPort]; the local [dev.kmpilot.todo.auth.LocalAuthScaffold] (a users table +
 * hashed pw) is replaced by a server that issues + validates real Firebase ID tokens (JWTs). The mapping is again
 * deliberately thin:
 *  - [signUp]   → `auth.createUserWithEmailAndPassword(email, password)`  (instant login on the emulator)
 *  - [signIn]   → `auth.signInWithEmailAndPassword(email, password)`
 *  - [signOut]  → `auth.signOut()`
 *  - [session]  → maps `auth.authStateChanged` (Flow<FirebaseUser?>): a signed-in [FirebaseUser] → a domain [Session].
 *
 * The ID token the signed-in user carries is what Firestore's security rules read as `request.auth.uid`, so
 * [FirebaseTaskRepository] carries NO ownership logic beyond constraining its query — the rules enforce ownership
 * server-side, exactly as Postgres RLS does for Supabase (just by a different mechanism; see that repo's docs).
 */
class FirebaseAuthAdapter(
    private val auth: FirebaseAuth = Firebase.auth,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : AuthPort {

    override val session: StateFlow<Session?> =
        auth.authStateChanged
            .map { user -> user?.toSession() }
            .stateIn(scope, SharingStarted.Eagerly, auth.currentUser?.toSession())

    override suspend fun signUp(email: String, password: String, displayName: String): Result<Session> =
        runCatching {
            val result = auth.createUserWithEmailAndPassword(email.trim(), password)
            // Firebase signs the user in as part of account creation → the client now holds an authenticated user.
            (result.user ?: auth.currentUser)?.toSession()
                ?: error("Firebase returned no user after createUserWithEmailAndPassword")
        }

    override suspend fun signIn(email: String, password: String): Result<Session> =
        runCatching {
            val result = auth.signInWithEmailAndPassword(email.trim(), password)
            (result.user ?: auth.currentUser)?.toSession()
                ?: error("Firebase returned no user after signInWithEmailAndPassword")
        }

    override fun signOut() {
        // The port's signOut is synchronous; the GitLive call is suspend, so bridge it (tests run single-threaded).
        runBlocking { auth.signOut() }
    }

    /** OAuth is a separate (browser/credential) flow — out of scope for this server-CRUD proof, as on Supabase. */
    override suspend fun signInWith(provider: String): Result<Session> =
        Result.failure(NotImplementedError("OAuth ($provider) is a separate consumed-service flow, not part of this adapter"))

    /** Map a Firebase [FirebaseUser] to the app's [Session]. Firebase email/password has no display name → use email. */
    private fun FirebaseUser.toSession(): Session {
        val mail = email ?: ""
        return Session(userId = uid, email = mail, displayName = mail)
    }
}
