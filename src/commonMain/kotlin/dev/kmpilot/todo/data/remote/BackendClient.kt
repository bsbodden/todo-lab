package dev.kmpilot.todo.data.remote

import dev.kmpilot.todo.domain.AlarmInterval
import dev.kmpilot.todo.domain.Task
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

/** Wire DTO (serializable; transport-shaped, not domain-shaped). */
@Serializable
data class TaskDto(
    val id: Long = 0,
    val title: String,
    val description: String? = null,
    val categoryId: Long? = null,
    val dueDate: String? = null,
    val creationDate: String? = null,
    val completedDate: String? = null,
    val isCompleted: Boolean = false,
    val isRepeating: Boolean = false,
    val alarmInterval: String? = null,
)

/**
 * The BYOK remote SEAM: a thin transport contract that any backend SDK can satisfy
 * (Supabase / PocketBase / Firebase / your own DDD backend). [RemoteTaskRepository] maps it to the
 * domain [dev.kmpilot.todo.data.TaskRepository] port, so the rest of the app is backend-agnostic.
 */
interface BackendClient {
    suspend fun list(): List<TaskDto>
    suspend fun get(id: Long): TaskDto?
    suspend fun upsert(dto: TaskDto): TaskDto
    suspend fun delete(id: Long)
}

fun Task.toDto() = TaskDto(
    id = id, title = title, description = description, categoryId = categoryId,
    dueDate = dueDate?.toString(), creationDate = creationDate?.toString(),
    completedDate = completedDate?.toString(), isCompleted = isCompleted,
    isRepeating = isRepeating, alarmInterval = alarmInterval?.name,
)

fun TaskDto.toDomain() = Task(
    id = id, title = title, description = description, categoryId = categoryId,
    dueDate = dueDate?.let { LocalDateTime.parse(it) },
    creationDate = creationDate?.let { LocalDateTime.parse(it) },
    completedDate = completedDate?.let { LocalDateTime.parse(it) },
    isCompleted = isCompleted, isRepeating = isRepeating,
    alarmInterval = alarmInterval?.let { AlarmInterval.valueOf(it) },
)
