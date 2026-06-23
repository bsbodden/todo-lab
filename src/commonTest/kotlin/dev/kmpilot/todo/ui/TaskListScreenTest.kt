package dev.kmpilot.todo.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import dev.kmpilot.todo.domain.Task
import dev.kmpilot.todo.presentation.TaskListUi
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TaskListScreenTest {

    @Test fun content_renders_tasks_and_checkbox_toggles_the_right_one() = runComposeUiTest {
        val ui = TaskListUi.Content(listOf(Task(1, "Buy milk"), Task(2, "Walk dog")))
        var toggled: Task? = null
        var added = false
        setContent {
            TaskListScreen(ui, onToggle = { toggled = it }, onOpen = {}, onAdd = { added = true }, onRetry = {})
        }
        onNodeWithText("Buy milk").assertExists()
        onNodeWithText("Walk dog").assertExists()

        onNodeWithTag("check_2").performClick()
        assertEquals(2L, toggled?.id, "the tapped task's checkbox must toggle that task")

        onNodeWithTag("add").performClick()
        assertEquals(true, added)
    }

    @Test fun empty_state_shows_done() = runComposeUiTest {
        setContent { TaskListScreen(TaskListUi.Empty, onToggle = {}, onOpen = {}, onAdd = {}, onRetry = {}) }
        onNodeWithTag("empty").assertExists()
    }

    @Test fun loading_state_shows_spinner() = runComposeUiTest {
        setContent { TaskListScreen(TaskListUi.Loading, onToggle = {}, onOpen = {}, onAdd = {}, onRetry = {}) }
        onNodeWithTag("loading").assertExists()
    }

    @Test fun error_state_shows_message_and_retry() = runComposeUiTest {
        var retried = false
        setContent { TaskListScreen(TaskListUi.Error("boom"), onToggle = {}, onOpen = {}, onAdd = {}, onRetry = { retried = true }) }
        onNodeWithTag("error").assertExists()
        onNodeWithTag("retry").performClick()
        assertEquals(true, retried)
    }
}
