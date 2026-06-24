package dev.kmpilot.todo.data.firebase

import android.app.Application
import com.google.firebase.FirebasePlatform
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.firestoreSettings
import dev.gitlive.firebase.firestore.memoryCacheSettings
import dev.gitlive.firebase.initialize

/**
 * One-shot, idempotent bootstrap for the GitLive firebase-kotlin-sdk on the **JVM**, pointed at the local Firebase
 * Emulator Suite (Auth 127.0.0.1:9099, Firestore 127.0.0.1:8080, project `demo-todo`).
 *
 * This is the jvm-only half of the backend SWAP's Firebase variant (`docs/backend/embeddable-vs-server.md`). The app
 * keeps depending only on the ports ([dev.kmpilot.todo.auth.AuthPort], [dev.kmpilot.todo.data.TaskRepository]); this
 * just stands the SDK up. It mirrors what [dev.kmpilot.todo.data.supabase.SupabaseClientFactory] does for Supabase,
 * except Firebase needs a process-global init (there is no per-call client handle), so the wiring is a singleton.
 *
 * Three JVM-specific things the Android SDK gives you for free, done here by hand:
 *  1. **A [FirebasePlatform]** — firebase-java-sdk's substitute for the Android `Context`. It supplies key/value
 *     storage (the persisted auth token) + logging. An in-memory map is right for the emulator/tests.
 *  2. **Fake [FirebaseOptions]** — no real credentials against the emulator; only `projectId` must equal the running
 *     emulator project (`demo-todo`). `applicationId`/`apiKey` are required by the builder but may be fakes.
 *  3. **Memory cache, not on-disk** — firebase-java-sdk's offline (LevelDB) persistence is unreliable on the JVM, so
 *     we select the in-memory cache. That removes any writable-persistence-dir requirement entirely.
 *
 * Ordering rule (load-bearing): `useEmulator(...)` must be called right after init and BEFORE the first network op,
 * and Firestore's `settings` must be set before its first use. Calling `useEmulator` after a request throws
 * IllegalStateException — so all of it happens once, here, behind a guard.
 */
object FirebaseBootstrap {

    const val PROJECT_ID: String = "demo-todo"   // MUST match the running emulator project (.firebaserc)
    const val AUTH_HOST: String = "127.0.0.1"
    const val AUTH_PORT: Int = 9099
    const val FIRESTORE_HOST: String = "127.0.0.1"
    const val FIRESTORE_PORT: Int = 8080

    @Volatile private var initialized = false

    /** Idempotent: safe to call from every test, from [dev.kmpilot.todo.Main], or repeatedly within a process. */
    @Synchronized
    fun ensure() {
        if (initialized) return

        // 1) Install the JVM platform shim (firebase-java-sdk's substitute for the Android `Context` services).
        //    Guarded: initializeFirebasePlatform throws if called twice, and another module/test may already have
        //    installed one.
        runCatching {
            FirebasePlatform.initializeFirebasePlatform(object : FirebasePlatform() {
                private val store = mutableMapOf<String, String>()
                override fun store(key: String, value: String) { store[key] = value }
                override fun retrieve(key: String): String? = store[key]
                override fun clear(key: String) { store.remove(key) }
                override fun log(msg: String) { /* quiet by default; flip to println(msg) when debugging */ }
            })
        }

        // 2) Initialize the default app with fake options. GitLive's JVM `initialize` casts `context` to a Context and
        //    forwards to firebase-java-sdk, so we pass an `android.app.Application()` — the firebase-java-sdk's stub
        //    Android Context (NOT null; null would fail the non-null `context as Context` cast). This is exactly what
        //    GitLive's own JVM test harness passes (test-utils' `testContext`).
        Firebase.initialize(
            context = Application(),
            options = FirebaseOptions(
                projectId = PROJECT_ID,                                      // the only field the emulator checks
                applicationId = "1:000000000000:android:0000000000000000",   // fake — accepted by the builder
                apiKey = "fake-api-key",                                     // fake — accepted by the emulator
            ),
        )

        // 3) Memory cache → no writable LevelDB dir needed on the JVM. Set BEFORE first Firestore use.
        Firebase.firestore.settings = firestoreSettings {
            cacheSettings = memoryCacheSettings { }
        }

        // 4) Point both products at the emulators — immediately after init, before any auth/firestore network op.
        Firebase.auth.useEmulator(AUTH_HOST, AUTH_PORT)
        Firebase.firestore.useEmulator(FIRESTORE_HOST, FIRESTORE_PORT)

        initialized = true
    }
}
