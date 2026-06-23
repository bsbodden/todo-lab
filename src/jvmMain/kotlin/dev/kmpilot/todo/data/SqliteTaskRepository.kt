package dev.kmpilot.todo.data

import dev.kmpilot.todo.domain.AlarmInterval
import dev.kmpilot.todo.domain.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDateTime
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Types

/**
 * LOCAL-SQL adapter (JVM, JDBC + SQLite). Same [TaskRepository] port as everything else.
 * In a full KMP app the production local-SQL default is **SQLDelight** (multiplatform, typed);
 * this JDBC version is the runnable JVM stand-in for the lab — the port and the contract are identical.
 */
class SqliteTaskRepository(jdbcUrl: String = "jdbc:sqlite::memory:") : TaskRepository {
    private val conn: Connection = DriverManager.getConnection(jdbcUrl)
    private val tasks = MutableStateFlow<List<Task>>(emptyList())

    init {
        conn.createStatement().use {
            it.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS task(
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  title TEXT NOT NULL,
                  description TEXT,
                  category_id INTEGER,
                  due_date TEXT, creation_date TEXT, completed_date TEXT,
                  is_completed INTEGER NOT NULL DEFAULT 0,
                  is_repeating INTEGER NOT NULL DEFAULT 0,
                  alarm_interval TEXT
                )
                """.trimIndent()
            )
        }
        refresh()
    }

    override fun observeAll(): Flow<List<Task>> = tasks.asStateFlow()
    override suspend fun all(): List<Task> = tasks.value
    override suspend fun byId(id: Long): Task? = tasks.value.find { it.id == id }

    override suspend fun upsert(task: Task): Task {
        val saved = if (task.id <= 0L) {
            conn.prepareStatement(
                "INSERT INTO task(title,description,category_id,due_date,creation_date,completed_date,is_completed,is_repeating,alarm_interval) VALUES(?,?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS,
            ).use { ps ->
                ps.bind(task); ps.executeUpdate()
                ps.generatedKeys.use { keys -> keys.next(); task.copy(id = keys.getLong(1)) }
            }
        } else {
            conn.prepareStatement(
                "UPDATE task SET title=?,description=?,category_id=?,due_date=?,creation_date=?,completed_date=?,is_completed=?,is_repeating=?,alarm_interval=? WHERE id=?",
            ).use { ps -> ps.bind(task); ps.setLong(10, task.id); ps.executeUpdate(); task }
        }
        refresh()
        return saved
    }

    override suspend fun delete(id: Long) {
        conn.prepareStatement("DELETE FROM task WHERE id=?").use { it.setLong(1, id); it.executeUpdate() }
        refresh()
    }

    private fun PreparedStatement.bind(t: Task) {
        setString(1, t.title)
        setString(2, t.description)
        if (t.categoryId != null) setLong(3, t.categoryId) else setNull(3, Types.INTEGER)
        setString(4, t.dueDate?.toString())
        setString(5, t.creationDate?.toString())
        setString(6, t.completedDate?.toString())
        setInt(7, if (t.isCompleted) 1 else 0)
        setInt(8, if (t.isRepeating) 1 else 0)
        setString(9, t.alarmInterval?.name)
    }

    private fun refresh() {
        val out = mutableListOf<Task>()
        conn.createStatement().use { st ->
            st.executeQuery("SELECT * FROM task ORDER BY id").use { rs -> while (rs.next()) out += rs.toTask() }
        }
        tasks.value = out
    }

    private fun ResultSet.toTask(): Task {
        val cat = getLong("category_id"); val categoryId = if (wasNull()) null else cat
        return Task(
            id = getLong("id"),
            title = getString("title"),
            description = getString("description"),
            categoryId = categoryId,
            dueDate = getString("due_date")?.let { LocalDateTime.parse(it) },
            creationDate = getString("creation_date")?.let { LocalDateTime.parse(it) },
            completedDate = getString("completed_date")?.let { LocalDateTime.parse(it) },
            isCompleted = getInt("is_completed") == 1,
            isRepeating = getInt("is_repeating") == 1,
            alarmInterval = getString("alarm_interval")?.let { AlarmInterval.valueOf(it) },
        )
    }
}
