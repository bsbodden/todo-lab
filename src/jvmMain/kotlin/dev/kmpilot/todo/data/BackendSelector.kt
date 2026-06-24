package dev.kmpilot.todo.data

import dev.kmpilot.todo.auth.AuthPort
import dev.kmpilot.todo.auth.LocalAuthScaffold
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
 * to it. `BACKEND=supabase` → the real backend (ownership enforced server-side by Postgres RLS, plain-CRUD adapter);
 * anything else → the local dev scaffolding (SQLite + the on-device [ScopedTaskRepository] ownership decorator).
 *
 * (This lives in jvmMain because the Supabase adapter is jvm-only for now; the *choice of which real backend* is a
 * generation step — KMPilot emits only the chosen one's code + deps — whereas local↔real is this runtime config flip.)
 */
data class Backend(val label: String, val auth: AuthPort, val repo: TaskRepository)

object BackendSelector {

    private val systemNow: () -> LocalDateTime =
        { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }

    /** Pick by config (the `BACKEND` env var). The default is the local scaffold — zero-config dev. */
    fun fromEnv(): Backend = when (System.getenv("BACKEND")?.trim()?.lowercase()) {
        "supabase" -> supabase()
        else -> local()
    }

    /** Local dev scaffolding: SQLite + scaffold auth + the on-device ownership decorator. */
    fun local(now: () -> LocalDateTime = systemNow): Backend {
        val auth = LocalAuthScaffold(now)
        val base: TaskRepository = SqlDelightTaskRepository(todoDatabase(jvmSqliteDriver()))
        return Backend("local-scaffold", auth, ScopedTaskRepository(base, auth))
    }

    /**
     * The chosen REAL backend — Supabase. SAME ports; the on-device decorator is GONE: [SupabaseTaskRepository] is
     * plain PostgREST CRUD and the database's RLS does the scoping. URL/key default to the local Docker stack.
     */
    fun supabase(
        apiUrl: String = System.getenv("SUPABASE_URL") ?: "http://127.0.0.1:54321",
        anonKey: String = System.getenv("SUPABASE_ANON_KEY") ?: LOCAL_ANON_KEY,
    ): Backend {
        val client = SupabaseClientFactory.create(apiUrl, anonKey)
        return Backend("supabase", SupabaseAuthAdapter(client), SupabaseTaskRepository(client))
    }

    // The well-known Supabase LOCAL-dev anon key (not a secret — only works against a local stack).
    private const val LOCAL_ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0"
}
