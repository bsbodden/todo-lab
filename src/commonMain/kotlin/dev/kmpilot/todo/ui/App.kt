package dev.kmpilot.todo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import dev.kmpilot.todo.data.InMemoryTaskRepository
import dev.kmpilot.todo.data.TaskRepository
import dev.kmpilot.todo.domain.AlarmInterval
import dev.kmpilot.todo.domain.Task
import dev.kmpilot.todo.presentation.RootComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.time.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** The shared app UI — identical on Android, iOS, and the wasm preview. Each platform entrypoint calls App(root). */
@Composable
fun App(root: RootComponent) {
    MaterialTheme { RootContent(root) }
}

/**
 * Builds the root component (resumed lifecycle + a Main-dispatcher scope), used by the Android/iOS entrypoints.
 *
 * The persistence binding here is the in-memory adapter seeded with a small default list — the SIMPLEST working
 * repository so the task list is immediately usable on the real mobile targets (the BYOK seam can later swap in
 * a platform SQLDelight driver). The wasm preview keeps its own Koin-driven scenario wiring in main.kt.
 */
fun buildRoot(scope: CoroutineScope = CoroutineScope(Dispatchers.Main)): RootComponent {
    val lifecycle = LifecycleRegistry()
    val repo: TaskRepository = InMemoryTaskRepository(defaultSeed())
    val now: () -> LocalDateTime = { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }
    val root = RootComponent(DefaultComponentContext(lifecycle), scope, repo, now)
    lifecycle.resume()
    return root
}

private fun defaultSeed(): List<Task> {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return listOf(
        Task(id = 1, title = "Buy oat milk", creationDate = now),
        Task(id = 2, title = "Ship the minimap prototype", creationDate = now),
        Task(id = 3, title = "Water plants", isRepeating = true, alarmInterval = AlarmInterval.WEEKLY, creationDate = now),
    )
}
