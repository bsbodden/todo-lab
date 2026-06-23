package dev.kmpilot.todo.data.remote

import dev.kmpilot.todo.data.assertPagingContract
import dev.kmpilot.todo.data.assertRepositoryContract
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * The Ktor remote/BYOK adapter satisfies the SAME `TaskRepository` contract as the local adapters —
 * proven over real JSON-on-HTTP (Ktor + kotlinx.serialization through a MockEngine backend). This is the
 * BYOK guarantee made concrete: swap local SQLDelight for a remote backend, the app is unchanged.
 */
class RemoteTaskRepositoryTest {
    private fun remote() = RemoteTaskRepository(
        RestBackendClient(jsonHttpClient(fakeRestBackend()), baseUrl = "https://api.test"),
    )

    @Test fun satisfies_the_same_repository_contract() = runTest {
        assertRepositoryContract(remote())
    }

    @Test fun satisfies_the_paging_contract() = runTest {
        assertPagingContract(remote())
    }
}
