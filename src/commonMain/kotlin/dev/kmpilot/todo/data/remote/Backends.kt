package dev.kmpilot.todo.data.remote

import dev.kmpilot.todo.data.InMemoryTaskRepository
import dev.kmpilot.todo.data.TaskRepository

/**
 * Concrete BYOK backend clients. Each is a [BackendClient] the user configures with THEIR keys/URL;
 * the bodies are skeletons (the lab runs offline) — wire them with Ktor + kotlinx.serialization.
 * The point is the SHAPE: every one plugs into [RemoteTaskRepository] behind the same port.
 */

/** Supabase (Postgres + PostgREST). BYOK = project URL + anon key. */
class SupabaseBackendClient(
    val url: String,
    val anonKey: String,
    val table: String = "tasks",
) : BackendClient {
    override suspend fun list(): List<TaskDto> =
        TODO("GET \$url/rest/v1/\$table?select=*  (apikey + Authorization: Bearer \$anonKey)")
    override suspend fun get(id: Long): TaskDto? =
        TODO("GET \$url/rest/v1/\$table?id=eq.\$id")
    override suspend fun upsert(dto: TaskDto): TaskDto =
        TODO("POST/PATCH \$table  (Prefer: return=representation, resolution=merge-duplicates)")
    override suspend fun delete(id: Long) =
        TODO("DELETE \$url/rest/v1/\$table?id=eq.\$id")
}

/** PocketBase. BYOK = base URL (+ optional auth token). */
class PocketBaseBackendClient(
    val baseUrl: String,
    val collection: String = "tasks",
) : BackendClient {
    override suspend fun list(): List<TaskDto> =
        TODO("GET \$baseUrl/api/collections/\$collection/records")
    override suspend fun get(id: Long): TaskDto? =
        TODO("GET \$baseUrl/api/collections/\$collection/records/\$id")
    override suspend fun upsert(dto: TaskDto): TaskDto =
        TODO("POST (create) / PATCH (update) \$baseUrl/api/collections/\$collection/records")
    override suspend fun delete(id: Long) =
        TODO("DELETE \$baseUrl/api/collections/\$collection/records/\$id")
}

/** Firebase Firestore. BYOK = project id + api key (REST) or the SDK. */
class FirebaseBackendClient(
    val projectId: String,
    val apiKey: String,
) : BackendClient {
    override suspend fun list(): List<TaskDto> =
        TODO("GET https://firestore.googleapis.com/v1/projects/\$projectId/databases/(default)/documents/tasks")
    override suspend fun get(id: Long): TaskDto? = TODO("GET .../documents/tasks/\$id")
    override suspend fun upsert(dto: TaskDto): TaskDto = TODO("PATCH .../documents/tasks/{id}")
    override suspend fun delete(id: Long) = TODO("DELETE .../documents/tasks/\$id")
}

/** The whole BYOK switch in one place — flip the backend, the app never changes. */
enum class Backend { IN_MEMORY, LOCAL_SQLITE, SUPABASE, POCKETBASE, FIREBASE }

/**
 * The ONE-LINE swap (would normally live in the Koin DI module). `LOCAL_SQLITE` is JVM-only so it's
 * created by the platform layer; here we show the binding intent.
 */
fun taskRepository(backend: Backend, config: Map<String, String> = emptyMap()): TaskRepository = when (backend) {
    Backend.IN_MEMORY -> InMemoryTaskRepository()
    Backend.SUPABASE -> RemoteTaskRepository(SupabaseBackendClient(config.getValue("url"), config.getValue("anonKey")))
    Backend.POCKETBASE -> RemoteTaskRepository(PocketBaseBackendClient(config.getValue("baseUrl")))
    Backend.FIREBASE -> RemoteTaskRepository(FirebaseBackendClient(config.getValue("projectId"), config.getValue("apiKey")))
    Backend.LOCAL_SQLITE -> error("LOCAL_SQLITE is created in the platform (JVM/Android) layer; see SqliteTaskRepository")
}
