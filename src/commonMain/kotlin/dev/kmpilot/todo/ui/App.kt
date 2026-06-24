package dev.kmpilot.todo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import dev.kmpilot.todo.auth.AuthPort
import dev.kmpilot.todo.auth.InMemoryUserStore
import dev.kmpilot.todo.auth.LocalAuthScaffold
import dev.kmpilot.todo.auth.UserStore
import dev.kmpilot.todo.data.InMemoryTaskRepository
import dev.kmpilot.todo.data.ScopedTaskRepository
import dev.kmpilot.todo.data.SqlDelightTaskRepository
import dev.kmpilot.todo.data.SqlDelightUserStore
import dev.kmpilot.todo.data.TaskRepository
import dev.kmpilot.todo.data.todoDatabase
import dev.kmpilot.todo.data.todoDriverOrNull
import dev.kmpilot.todo.presentation.RootComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.time.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The shared app UI — identical on Android, iOS, and the wasm preview. Each platform entrypoint calls App(root),
 * so the SIGNATURE stays the same; the session gate lives entirely inside here. Signed out → [LoginScreen];
 * signed in → the normal [RootContent].
 */
@Composable
fun App(root: RootComponent) {
    MaterialTheme {
        val session by root.auth.session.collectAsState()
        if (session == null) LoginScreen(root.auth) else RootContent(root)
    }
}

/**
 * Builds the root component (resumed lifecycle + a Main-dispatcher scope), used by the Android/iOS entrypoints.
 *
 * The full local stack: [LocalAuthScaffold] (identity) → a base task repo → wrapped in [ScopedTaskRepository] so
 * every read/write is owner-scoped to the signed-in user. The app boots SIGNED OUT (LoginScreen); a brand-new
 * account sees an EMPTY list — the visible proof of per-user isolation.
 *
 * Persistence is chosen PER PLATFORM via [todoDriverOrNull]: when a platform supplies a durable SqlDriver
 * (Android/iOS device storage, desktop file), tasks + users persist across app restarts via
 * [SqlDelightTaskRepository] + [SqlDelightUserStore]; otherwise (wasm preview) it falls back to the in-memory
 * repo + user store, unchanged. The BYOK seam can later swap the scaffold auth for a real backend with no
 * entrypoint changes. The wasm preview keeps its own Koin-driven scenario wiring in main.kt.
 */
fun buildRoot(scope: CoroutineScope = CoroutineScope(Dispatchers.Main)): RootComponent {
    val lifecycle = LifecycleRegistry()
    val now: () -> LocalDateTime = { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }

    val driver = todoDriverOrNull()
    val base: TaskRepository
    val userStore: UserStore
    if (driver != null) {
        val db = todoDatabase(driver)            // wires the TaskEntity column adapters (see SqlDelightTaskRepository)
        base = SqlDelightTaskRepository(db)
        userStore = SqlDelightUserStore(db)
    } else {
        base = InMemoryTaskRepository()
        userStore = InMemoryUserStore()
    }

    val auth: AuthPort = LocalAuthScaffold(now, userStore)
    val repo: TaskRepository = ScopedTaskRepository(base, auth)
    val root = RootComponent(DefaultComponentContext(lifecycle), scope, repo, now, auth)
    lifecycle.resume()
    return root
}
