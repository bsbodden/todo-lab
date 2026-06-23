package dev.kmpilot.todo.presentation

import dev.kmpilot.todo.domain.Task
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The statechart's behavior IS the spec — these assertions mirror what a model-based generator would emit. */
class TaskListMachineTest {
    private val a = Task(id = 1, title = "a")

    @Test fun starts_in_loading() = runTest {
        val m = TaskListMachine(backgroundScope).also { it.start() }
        assertEquals(TaskListUi.Loading, m.ui.value)
    }

    @Test fun loaded_routes_to_content_or_empty_by_emptiness() = runTest {
        val m = TaskListMachine(backgroundScope).also { it.start() }
        m.onLoaded(listOf(a))
        assertEquals(TaskListUi.Content(listOf(a)), m.ui.value)
        m.onLoaded(emptyList())
        assertEquals(TaskListUi.Empty, m.ui.value)        // re-routes Content -> Empty
        m.onLoaded(listOf(a))
        assertEquals(TaskListUi.Content(listOf(a)), m.ui.value)
    }

    @Test fun reload_while_in_content_reflects_the_new_list() = runTest {
        // The Content -> Content case (a toggle removes one, or a rename changes one). Must re-render —
        // this is the self-transition bug that froze the list after the first load.
        val b = Task(2, "b")
        val m = TaskListMachine(backgroundScope).also { it.start() }
        m.onLoaded(listOf(a, b))
        assertEquals(TaskListUi.Content(listOf(a, b)), m.ui.value)
        m.onLoaded(listOf(a))                              // b completed/removed — still Content
        assertEquals(TaskListUi.Content(listOf(a)), m.ui.value)
        m.onLoaded(listOf(a.copy(title = "renamed")))      // a renamed — still Content, must reflect it
        assertEquals(TaskListUi.Content(listOf(a.copy(title = "renamed"))), m.ui.value)
    }

    @Test fun failure_then_retry_returns_to_loading() = runTest {
        val m = TaskListMachine(backgroundScope).also { it.start() }
        m.onLoadFailed("boom")
        assertEquals(TaskListUi.Error("boom"), m.ui.value)
        m.retry()
        assertEquals(TaskListUi.Loading, m.ui.value)
    }

    @Test fun illegal_transition_is_a_noop() = runTest {
        // LoadFailed is only legal from Loading; once in Content it's ignored — no crash, no state change.
        val m = TaskListMachine(backgroundScope).also { it.start() }
        m.onLoaded(listOf(a))
        m.onLoadFailed("ignored")
        assertEquals(TaskListUi.Content(listOf(a)), m.ui.value)
    }
}
