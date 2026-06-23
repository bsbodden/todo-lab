package dev.kmpilot.todo.data.remote

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A MockEngine emulating a REST `tasks` backend entirely in-memory (Long ids, autoincrement). It lets the
 * Ktor [RestBackendClient] exercise the REAL wire format (serialize → HTTP → deserialize) without a live
 * server, so [dev.kmpilot.todo.data.assertRepositoryContract] runs against the actual transport.
 */
fun fakeRestBackend(): MockEngine {
    val store = linkedMapOf<Long, TaskDto>()
    var nextId = 1L
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    return MockEngine { request ->
        val segments = request.url.encodedPath.trim('/').split('/')
        val id = segments.getOrNull(1)?.toLongOrNull()
        val bodyText = { (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString() }
        when (request.method) {
            HttpMethod.Get ->
                if (id == null) {
                    respond(json.encodeToString(store.values.toList()), HttpStatusCode.OK, jsonHeaders)
                } else {
                    store[id]?.let { respond(json.encodeToString(it), HttpStatusCode.OK, jsonHeaders) }
                        ?: respond("", HttpStatusCode.NotFound)
                }
            HttpMethod.Post -> {
                val created = json.decodeFromString<TaskDto>(bodyText()).copy(id = nextId++)
                store[created.id] = created
                respond(json.encodeToString(created), HttpStatusCode.Created, jsonHeaders)
            }
            HttpMethod.Put -> {
                val updated = json.decodeFromString<TaskDto>(bodyText())
                store[id!!] = updated
                respond(json.encodeToString(updated), HttpStatusCode.OK, jsonHeaders)
            }
            HttpMethod.Delete -> {
                store.remove(id)
                respond("", HttpStatusCode.NoContent)
            }
            else -> respond("", HttpStatusCode.MethodNotAllowed)
        }
    }
}
