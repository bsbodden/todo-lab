package dev.kmpilot.todo

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import dev.kmpilot.todo.data.TaskRepository
import dev.kmpilot.todo.di.Now
import dev.kmpilot.todo.di.appModule
import dev.kmpilot.todo.di.jvmPersistenceModule
import dev.kmpilot.todo.domain.AlarmInterval
import dev.kmpilot.todo.domain.Task
import dev.kmpilot.todo.presentation.RootComponent
import dev.kmpilot.todo.ui.RootContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin

/** Runnable Compose Desktop entry point. `gradle run` opens the app on the JVM. */
fun main() {
    val lifecycle = LifecycleRegistry()
    val scope = CoroutineScope(Dispatchers.Default)
    // DI: same appModule as the preview; only the persistence module differs (real local-SQL adapter).
    val koin = startKoin { modules(appModule, jvmPersistenceModule) }.koin
    val repo = koin.get<TaskRepository>()
    val now = koin.get<Now>()
    runBlocking {
        repo.upsert(Task(id = 0, title = "Buy oat milk", creationDate = now()))
        repo.upsert(Task(id = 0, title = "Ship the minimap prototype", creationDate = now()))
        repo.upsert(Task(id = 0, title = "Water plants", isRepeating = true, alarmInterval = AlarmInterval.WEEKLY, creationDate = now()))
    }
    // Decompose's childStack must be created on the AWT event thread, not the bare "main" thread.
    val root = onEventThread {
        RootComponent(DefaultComponentContext(lifecycle), scope, repo, now::invoke).also { lifecycle.resume() }
    }
    application {
        Window(onCloseRequest = ::exitApplication, title = "todo-lab — KMPilot stack") {
            MaterialTheme { RootContent(root) }
        }
    }
}

private fun <T> onEventThread(block: () -> T): T {
    if (javax.swing.SwingUtilities.isEventDispatchThread()) return block()
    var result: T? = null
    javax.swing.SwingUtilities.invokeAndWait { result = block() }
    @Suppress("UNCHECKED_CAST")
    return result as T
}
