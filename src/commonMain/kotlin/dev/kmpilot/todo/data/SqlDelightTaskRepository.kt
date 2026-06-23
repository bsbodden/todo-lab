package dev.kmpilot.todo.data

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.db.SqlDriver
import dev.kmpilot.todo.db.TaskEntity
import dev.kmpilot.todo.db.TodoDatabase
import dev.kmpilot.todo.domain.AlarmInterval
import dev.kmpilot.todo.domain.Task
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime

/** ISO-8601 text ⇄ LocalDateTime — keeps the domain model framework-free; mapping lives at the storage edge. */
private val localDateTimeAdapter = object : ColumnAdapter<LocalDateTime, String> {
    override fun decode(databaseValue: String): LocalDateTime = LocalDateTime.parse(databaseValue)
    override fun encode(value: LocalDateTime): String = value.toString()
}

/** Enum name ⇄ AlarmInterval. */
private val alarmIntervalAdapter = object : ColumnAdapter<AlarmInterval, String> {
    override fun decode(databaseValue: String): AlarmInterval = AlarmInterval.valueOf(databaseValue)
    override fun encode(value: AlarmInterval): String = value.name
}

/**
 * Wires the generated DB with its column adapters. This is the deterministic per-entity glue a generator
 * emits — NOT something to re-improvise per app. The engine (SQLDelight) supplies CRUD/streaming; we only
 * map types at the boundary.
 */
fun todoDatabase(driver: SqlDriver): TodoDatabase = TodoDatabase(
    driver = driver,
    TaskEntityAdapter = TaskEntity.Adapter(
        dueDateAdapter = localDateTimeAdapter,
        creationDateAdapter = localDateTimeAdapter,
        completedDateAdapter = localDateTimeAdapter,
        alarmIntervalAdapter = alarmIntervalAdapter,
    ),
)

/**
 * The SQLDelight local-SQL adapter — the opinionated on-device default (37% of KMP apps; the reference apps'
 * pick — see docs/kmp-corpus/STACK_ADOPTION.md). Same [TaskRepository] port as in-memory/remote; proven
 * interchangeable by [assertRepositoryContract]. This exact `commonMain` code compiles for android/ios too —
 * only the [SqlDriver] is platform-specific.
 */
class SqlDelightTaskRepository(
    db: TodoDatabase,
    private val io: CoroutineDispatcher = Dispatchers.Default,
) : TaskRepository {
    private val queries = db.taskQueries

    override fun observeAll(): Flow<List<Task>> =
        queries.selectAll().asFlow().mapToList(io).map { rows -> rows.map { it.toDomain() } }

    override suspend fun all(): List<Task> = withContext(io) {
        queries.selectAll().executeAsList().map { it.toDomain() }
    }

    override suspend fun byId(id: Long): Task? = withContext(io) {
        queries.selectById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun upsert(task: Task): Task = withContext(io) {
        queries.transactionWithResult {
            if (task.id <= 0L) {
                queries.insert(
                    title = task.title,
                    description = task.description,
                    categoryId = task.categoryId,
                    dueDate = task.dueDate,
                    creationDate = task.creationDate,
                    completedDate = task.completedDate,
                    isCompleted = task.isCompleted,
                    isRepeating = task.isRepeating,
                    alarmInterval = task.alarmInterval,
                )
                task.copy(id = queries.lastInsertedId().executeAsOne())
            } else {
                queries.update(
                    title = task.title,
                    description = task.description,
                    categoryId = task.categoryId,
                    dueDate = task.dueDate,
                    creationDate = task.creationDate,
                    completedDate = task.completedDate,
                    isCompleted = task.isCompleted,
                    isRepeating = task.isRepeating,
                    alarmInterval = task.alarmInterval,
                    id = task.id,
                )
                task
            }
        }
    }

    override suspend fun delete(id: Long): Unit = withContext(io) {
        queries.deleteById(id)
    }

    /** Override: push the keyset window down to SQLite (fetch limit+1 to detect [Page.hasMore]). */
    override suspend fun page(request: PageRequest): Page<Task> = withContext(io) {
        val after = request.afterId ?: 0L
        val rows = queries.selectPage(afterId = after, limit = (request.limit + 1).toLong())
            .executeAsList().map { it.toDomain() }
        val window = rows.take(request.limit)
        Page(items = window, nextCursor = window.lastOrNull()?.id, hasMore = rows.size > request.limit)
    }
}

private fun TaskEntity.toDomain() = Task(
    id = id,
    title = title,
    description = description,
    categoryId = categoryId,
    dueDate = dueDate,
    creationDate = creationDate,
    completedDate = completedDate,
    isCompleted = isCompleted,
    isRepeating = isRepeating,
    alarmInterval = alarmInterval,
)
