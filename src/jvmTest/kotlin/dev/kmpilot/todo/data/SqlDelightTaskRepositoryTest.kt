package dev.kmpilot.todo.data

import dev.kmpilot.todo.domain.Task
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The SAME contract as the in-memory and raw-JDBC adapters, now against real SQLDelight-generated SQL. */
class SqlDelightTaskRepositoryTest {
    @Test fun satisfies_the_same_repository_contract() = runTest {
        assertRepositoryContract(SqlDelightTaskRepository(todoDatabase(jvmSqliteDriver())))
    }

    /** Exercises the DB-pushed-down paging override — must tile identically to the default path. */
    @Test fun satisfies_the_paging_contract() = runTest {
        assertPagingContract(SqlDelightTaskRepository(todoDatabase(jvmSqliteDriver())))
    }

    /** The new ownerId column must round-trip through insert + update (it's what the local-RLS decorator filters on). */
    @Test fun persists_the_ownerId_column_through_insert_and_update() = runTest {
        val repo = SqlDelightTaskRepository(todoDatabase(jvmSqliteDriver()))
        val created = repo.upsert(Task(id = 0, title = "owned", ownerId = "u7"))
        assertEquals("u7", repo.byId(created.id)?.ownerId, "ownerId must survive insert")

        repo.upsert(created.copy(title = "re-owned", ownerId = "u9"))
        assertEquals("u9", repo.byId(created.id)?.ownerId, "ownerId must survive update")
        assertEquals(null, repo.upsert(Task(id = 0, title = "unowned")).ownerId, "default ownerId stays null")
    }
}
