package dev.kmpilot.todo.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** The SAME contract as the in-memory and raw-JDBC adapters, now against real SQLDelight-generated SQL. */
class SqlDelightTaskRepositoryTest {
    @Test fun satisfies_the_same_repository_contract() = runTest {
        assertRepositoryContract(SqlDelightTaskRepository(todoDatabase(jvmSqliteDriver())))
    }

    /** Exercises the DB-pushed-down paging override — must tile identically to the default path. */
    @Test fun satisfies_the_paging_contract() = runTest {
        assertPagingContract(SqlDelightTaskRepository(todoDatabase(jvmSqliteDriver())))
    }
}
