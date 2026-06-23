package dev.kmpilot.todo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.kmpilot.todo.domain.Task
import dev.kmpilot.todo.presentation.TaskListUi

/**
 * The MEAT screen, now rendered from the [TaskListUi] the [dev.kmpilot.todo.presentation.TaskListMachine]
 * projects. Pure: state in, callbacks out. Each interactive element carries a stable `testTag` so the
 * statechart's events can be replayed as real gestures in device-level model-based tests.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    ui: TaskListUi,
    onToggle: (Task) -> Unit,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Today") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, modifier = Modifier.testTag("add")) { Text("+") }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when (ui) {
                is TaskListUi.Loading -> CircularProgressIndicator(Modifier.testTag("loading"))
                is TaskListUi.Empty -> Text("All done 🎉", Modifier.testTag("empty"))
                is TaskListUi.Error -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(ui.message, Modifier.testTag("error"))
                    Button(onClick = onRetry, modifier = Modifier.testTag("retry")) { Text("Retry") }
                }
                is TaskListUi.Content -> LazyColumn(Modifier.fillMaxSize()) {
                    items(ui.tasks, key = { it.id }) { task ->
                        TaskRow(task, onToggle = { onToggle(task) }, onClick = { onOpen(task.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: Task, onToggle: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = task.isCompleted,
            onCheckedChange = { onToggle() },
            modifier = Modifier.testTag("check_${task.id}"),
        )
        Spacer(Modifier.width(12.dp))
        Text(task.title, Modifier.testTag("title_${task.id}"))
    }
}
