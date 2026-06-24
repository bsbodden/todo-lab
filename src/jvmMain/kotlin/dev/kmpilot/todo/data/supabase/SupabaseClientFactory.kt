package dev.kmpilot.todo.data.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Builds a real supabase-kt [SupabaseClient] pointed at a running Supabase instance.
 *
 * This is the JVM-side proof of the KMPilot backend SWAP (`docs/backend/embeddable-vs-server.md`): the SAME ports the
 * local scaffold uses ([dev.kmpilot.todo.auth.AuthPort], [dev.kmpilot.todo.data.TaskRepository]) get a thin remote
 * adapter, with NO change to the app. The factory installs exactly the two plugins the adapters need:
 *  - [Auth]      (GoTrue) — backs [SupabaseAuthAdapter] (sign up / in / out + the live session).
 *  - [Postgrest] (PostgREST over the `public` schema) — backs [SupabaseTaskRepository] (plain CRUD; RLS does ownership).
 *
 * Params are injectable so the conformance test can target the local Docker stack (127.0.0.1:54321 + the anon key).
 */
object SupabaseClientFactory {
    fun create(apiUrl: String, anonKey: String): SupabaseClient =
        createSupabaseClient(supabaseUrl = apiUrl, supabaseKey = anonKey) {
            install(Auth)
            install(Postgrest)
        }
}
