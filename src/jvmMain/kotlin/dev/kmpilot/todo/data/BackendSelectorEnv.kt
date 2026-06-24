package dev.kmpilot.todo.data

/**
 * The jvm-only convenience that reads the backend choice from environment variables — the desktop runner's
 * config seam. The SELECTION logic itself is multiplatform ([BackendSelector.select]); this just supplies the
 * env-derived values (which `System.getenv` makes JVM-only). On Android/iOS the choice would come from
 * BuildConfig / a settings screen instead, calling [BackendSelector.select] directly.
 *
 *  - `BACKEND=supabase` → the real backend, with `SUPABASE_URL` / `SUPABASE_ANON_KEY` overriding the local-stack
 *    defaults when set; anything else → the local scaffold (zero-config dev).
 */
fun BackendSelector.fromEnv(): Backend =
    when (System.getenv("BACKEND")?.trim()?.lowercase()) {
        "supabase" -> supabase(
            apiUrl = System.getenv("SUPABASE_URL") ?: BackendSelector.LOCAL_API_URL,
            anonKey = System.getenv("SUPABASE_ANON_KEY") ?: BackendSelector.LOCAL_ANON_KEY,
        )
        else -> local()
    }
