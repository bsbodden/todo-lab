package dev.kmpilot.todo.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** A JSON Ktor client. The engine is injected so tests pass a MockEngine and apps pass a platform engine. */
fun jsonHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
    }
    expectSuccess = false // we inspect status (e.g. 404 → null) instead of throwing
}

/**
 * A real Ktor REST [BackendClient] over a standard JSON collection API (json-server / PostgREST-style,
 * Long ids): GET list, GET/{id}, POST (create → server assigns id), PUT/{id} (update), DELETE/{id}.
 *
 * This is the concrete BYOK transport the [RemoteTaskRepository] maps to the domain port. The
 * backend-specific clients (Supabase/PocketBase/Firebase) are thin variants of this shape — they differ
 * only in base URL, auth headers, and id convention (PocketBase uses string ids, noted in Backends.kt).
 * Proven by `RemoteTaskRepositoryTest` against a MockEngine that emulates the backend in-memory.
 */
class RestBackendClient(
    private val http: HttpClient,
    private val baseUrl: String,
    private val path: String = "tasks",
) : BackendClient {

    override suspend fun list(): List<TaskDto> = http.get("$baseUrl/$path").body()

    override suspend fun get(id: Long): TaskDto? {
        val resp = http.get("$baseUrl/$path/$id")
        return if (resp.status == HttpStatusCode.NotFound) null else resp.body()
    }

    override suspend fun upsert(dto: TaskDto): TaskDto =
        if (dto.id <= 0L) {
            http.post("$baseUrl/$path") { contentType(ContentType.Application.Json); setBody(dto) }.body()
        } else {
            http.put("$baseUrl/$path/${dto.id}") { contentType(ContentType.Application.Json); setBody(dto) }.body()
        }

    override suspend fun delete(id: Long) {
        http.delete("$baseUrl/$path/$id")
    }
}
