package dev.kmpilot.todo.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** The SAME contract as the in-memory adapter, against real local SQL — proves the backend swap. */
class SqliteTaskRepositoryTest {
    @Test fun satisfies_the_same_repository_contract() = runTest {
        assertRepositoryContract(SqliteTaskRepository()) // jdbc:sqlite::memory:
    }
}
