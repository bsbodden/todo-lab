package dev.kmpilot.todo.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class InMemoryTaskRepositoryTest {
    @Test fun satisfies_the_repository_contract() = runTest {
        assertRepositoryContract(InMemoryTaskRepository())
    }

    /** Exercises the DEFAULT (client-side) paging path on the port. */
    @Test fun satisfies_the_paging_contract() = runTest {
        assertPagingContract(InMemoryTaskRepository())
    }
}
