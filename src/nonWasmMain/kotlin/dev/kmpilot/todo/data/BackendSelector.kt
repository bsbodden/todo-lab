package dev.kmpilot.todo.data

import dev.kmpilot.todo.auth.AuthPort
import dev.kmpilot.todo.auth.InMemoryUserStore
import dev.kmpilot.todo.auth.LocalAuthScaffold
import dev.kmpilot.todo.auth.UserStore
import dev.kmpilot.todo.data.supabase.SupabaseAuthAdapter
import dev.kmpilot.todo.data.supabase.SupabaseClientFactory
import dev.kmpilot.todo.data.supabase.SupabaseTaskRepository
import kotlin.time.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * THE ONE TRUE CONFIG SWITCH (see `docs/backend/embeddable-vs-server.md` §"swap model"): **local scaffold ↔ the
 * chosen real backend, selected at startup by config** — with NO app-code change. Both adapters are present; the app
 * (UI, domain, `RootComponent`) depends ONLY on the ports [AuthPort] + [TaskRepository], so the selection is invisible
 * to it. `supabase` → the real backend (ownership enforced server-side by Postgres RLS, plain-CRUD adapter);
 * anything else → the local dev scaffolding (per-platform SQL + the on-device [ScopedTaskRepository] ownership decorator).
 *
 * MULTIPLATFORM: this selector lives in `nonWasmMain`, so the SAME swap compiles + links on the DEVICE targets
 * (Android, iOS) and the jvm test harness. supabase-kt is pure-Kotlin/Ktor (Kotlin/Native artifacts), so the Supabase
 * path links into the iOS framework and dexes into the Android APK; the wasm preview stays out of this set entirely.
 * The *choice of which real backend* is a generation step (KMPilot emits only the chosen one's code + deps); local↔real
 * is this runtime config flip. The jvm-only `fromEnv()` convenience (reads `System.getenv`) lives in jvmMain.
 */
data class Backend(val label: String, val auth: AuthPort, val repo: TaskRepository)

object BackendSelector {

    // The well-known Supabase LOCAL-dev anon key (not a secret — only works against a local stack). Public so the
    // jvm `fromEnv()` convenience can fall back to it when SUPABASE_ANON_KEY isn't set.
    const val LOCAL_ANON_KEY: String =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0"

    const val LOCAL_API_URL: String = "http://127.0.0.1:54321"

    private val systemNow: () -> LocalDateTime =
        { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }

    /**
     * MULTIPLATFORM selection point — pick a [Backend] by a plain config string (works identically on Android, iOS,
     * and jvm). `"supabase"` → the real backend; anything else → the local scaffold. The jvm runner derives [choice]
     * from `System.getenv("BACKEND")` via [fromEnv]; a device app could read it from BuildConfig / a settings screen.
     */
    fun select(choice: String?, now: () -> LocalDateTime = systemNow): Backend =
        when (choice?.trim()?.lowercase()) {
            "supabase" -> supabase()
            else -> local(now)
        }

    /**
     * Local dev scaffolding: per-platform durable SQL when a [todoDriverOrNull] driver is available (Android/iOS device
     * storage, desktop file), else the in-memory repo + user store — same fallback `buildRoot()` uses. Wrapped in the
     * scaffold auth + the on-device [ScopedTaskRepository] ownership decorator.
     */
    fun local(now: () -> LocalDateTime = systemNow): Backend {
        val driver = todoDriverOrNull()
        val base: TaskRepository
        val userStore: UserStore
        if (driver != null) {
            val db = todoDatabase(driver)
            base = SqlDelightTaskRepository(db)
            userStore = SqlDelightUserStore(db)
        } else {
            base = InMemoryTaskRepository()
            userStore = InMemoryUserStore()
        }
        val auth: AuthPort = LocalAuthScaffold(now, userStore)
        return Backend("local-scaffold", auth, ScopedTaskRepository(base, auth))
    }

    /**
     * The chosen REAL backend — Supabase. SAME ports; the on-device decorator is GONE: [SupabaseTaskRepository] is
     * plain PostgREST CRUD and the database's RLS does the scoping. URL/key default to the local Docker stack.
     * Params are explicit (no env reads) so this stays multiplatform; the jvm `fromEnv()` injects env-derived values.
     */
    fun supabase(
        apiUrl: String = LOCAL_API_URL,
        anonKey: String = LOCAL_ANON_KEY,
    ): Backend {
        val client = SupabaseClientFactory.create(apiUrl, anonKey)
        return Backend("supabase", SupabaseAuthAdapter(client), SupabaseTaskRepository(client))
    }
}
