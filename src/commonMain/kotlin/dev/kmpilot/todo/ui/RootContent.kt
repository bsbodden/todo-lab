package dev.kmpilot.todo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.arkivanov.decompose.extensions.compose.stack.Children
import dev.kmpilot.todo.presentation.RootComponent

/** Renders the Decompose child stack — each config maps to its screen. */
@Composable
fun RootContent(root: RootComponent) {
    val session by root.auth.session.collectAsState()
    Children(stack = root.stack) { child ->
        when (val instance = child.instance) {
            is RootComponent.Child.TaskList -> {
                val ui by instance.component.ui.collectAsState()
                TaskListScreen(
                    ui = ui,
                    onToggle = instance.component::toggle,
                    onOpen = instance.component.onOpen,
                    onAdd = instance.component.onAdd,
                    onRetry = instance.component::retry,
                    currentUserEmail = session?.email,
                    onSignOut = root.auth::signOut,
                )
            }
            is RootComponent.Child.AddTask -> AddTaskScreen(
                onSave = instance.component::add,
                onCancel = instance.component::cancel,
            )
            is RootComponent.Child.TaskDetail -> {
                val task by instance.component.task.collectAsState()
                TaskDetailScreen(
                    task = task,
                    onRename = instance.component::rename,
                    onBack = instance.component.onBack,
                )
            }
        }
    }
}
