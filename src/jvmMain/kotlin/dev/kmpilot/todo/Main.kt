package dev.kmpilot.todo

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import dev.kmpilot.todo.auth.AuthPort
import dev.kmpilot.todo.data.BackendSelector
import dev.kmpilot.todo.data.TaskRepository
import dev.kmpilot.todo.di.Now
import dev.kmpilot.todo.di.appModule
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
    val koin = startKoin { modules(appModule) }.koin
    val now = koin.get<Now>()
    // THE CONFIG SWITCH — `BACKEND=local|supabase` picks the backend with NO app-code change (see BackendSelector).
    // The app below depends only on the ports; the selected adapter is invisible to it.
    val backend = BackendSelector.fromEnv()
    println("[todo-lab] backend = ${backend.label}   (set BACKEND=supabase to run against the real backend)")
    val auth: AuthPort = backend.auth
    val repo: TaskRepository = backend.repo
    runBlocking {
        // convenience seed for the local scaffold only — the real backend starts at the login screen
        if (backend.label == "local-scaffold") {
            auth.signUp("demo@example.com", "demo", "Demo User") // owns the seeded tasks
            repo.upsert(Task(id = 0, title = "Buy oat milk", creationDate = now()))
            repo.upsert(Task(id = 0, title = "Ship the minimap prototype", creationDate = now()))
            repo.upsert(Task(id = 0, title = "Water plants", isRepeating = true, alarmInterval = AlarmInterval.WEEKLY, creationDate = now()))
        }
    }
    // Decompose's childStack must be created on the AWT event thread, not the bare "main" thread.
    val root = onEventThread {
        RootComponent(DefaultComponentContext(lifecycle), scope, repo, now::invoke, auth).also { lifecycle.resume() }
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
