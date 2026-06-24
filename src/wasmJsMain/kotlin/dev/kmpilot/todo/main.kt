package dev.kmpilot.todo

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import dev.kmpilot.todo.auth.AuthPort
import dev.kmpilot.todo.auth.LocalAuthScaffold
import dev.kmpilot.todo.data.FailingTaskRepository
import dev.kmpilot.todo.data.InMemoryTaskRepository
import dev.kmpilot.todo.data.ScopedTaskRepository
import dev.kmpilot.todo.data.TaskRepository
import dev.kmpilot.todo.di.Now
import dev.kmpilot.todo.di.appModule
import dev.kmpilot.todo.domain.AlarmInterval
import dev.kmpilot.todo.domain.Task
import dev.kmpilot.todo.presentation.RootComponent
import dev.kmpilot.todo.ui.App
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.context.startKoin
import org.koin.dsl.module

/**
 * The MOBILE preview entry point (the KMPilot Tier-1 preview). Renders the exact same `commonMain` Compose
 * UI a KMP Android/iOS app renders, into the browser canvas — no SDK/emulator/Xcode.
 *
 * **Scenarios / preconditions:** the app boots into a named precondition from `?scenario=NAME` (the frame's
 * Scenario dropdown sets it). This is the "LLM seeds a precondition, see it in the frame" loop — including the
 * Error state a healthy repo can never reach. The fixture is bound as the `TaskRepository`; the rest of the
 * app is unchanged (the BYOK seam).
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val scenario = scenarioParam()
    publishScenario(scenario)

    // Session-gated like the real app. The scaffold auth + a ScopedTaskRepository wrap the scenario fixture so
    // the preview exercises the SAME login gate + local-RLS path the mobile targets do.
    val auth: AuthPort = LocalAuthScaffold(now = { now() })
    val koin = startKoin {
        modules(
            appModule,
            module {
                single<AuthPort> { auth }
                single<TaskRepository> { ScopedTaskRepository(repoForScenario(scenario), auth) }
            },
        )
    }.koin

    val lifecycle = LifecycleRegistry()
    val scope = CoroutineScope(Dispatchers.Main)
    // Auto-sign-in a demo user so the scenario fixtures (owned by DEMO_USER_ID) render immediately. The demo
    // account is the FIRST user the scaffold creates → its userId is deterministically DEMO_USER_ID. "Sign out"
    // in the top bar returns to LoginScreen, demonstrating the gate + multi-user account switching.
    scope.launch { auth.signUp("demo@example.com", "demo", "Demo User") }
    val root = RootComponent(
        DefaultComponentContext(lifecycle),
        scope,
        koin.get<TaskRepository>(),
        koin.get<Now>()::invoke,
        koin.get<AuthPort>(),
    )
    lifecycle.resume()
    startBridgePosting() // stream chartSpec + live state to a parent studio (the minimap), cross-origin-safe
    startNavBridge(scope, root) // accept navigate commands from the studio (minimap click-to-navigate)
    // Full-window into document.body (Compose-for-wasm's happy path); the phone frame (index.html) iframes
    // and sizes this app. A sized sub-element paints blank on Chromium/Skiko — verified — so we don't.
    ComposeViewport(document.body!!) {
        App(root)
    }
}

/** When embedded in the studio, post the chart structure + live active state to the parent window. */
private fun startBridgePosting() {
    js("setInterval(function(){ try { if (window.parent && window.parent !== window) { window.parent.postMessage({ type: 'kmpilot', appGraph: globalThis.__appGraph, currentScreen: globalThis.__currentScreen, chartSpec: globalThis.__chartSpec, screen: globalThis.__screen }, '*'); } } catch (e) {} }, 400)")
}

/** Accept `{type:'kmpilot-cmd', navigate:'<Screen>'}` from the studio → navigate the running app. */
private fun startNavBridge(scope: CoroutineScope, root: RootComponent) {
    installNavListener()
    scope.launch {
        while (true) {
            delay(120)
            val cmd = readPendingNav()
            if (cmd.isNotEmpty()) { clearPendingNav(); root.navigateTo(cmd) }
        }
    }
}

// each js(...) must be a function's sole statement in Kotlin/Wasm
private fun installNavListener() {
    js("window.addEventListener('message', function(e){ if (e.data && e.data.type === 'kmpilot-cmd' && e.data.navigate) { globalThis.__pendingNav = e.data.navigate; } })")
}

private fun readPendingNav(): String = js("(globalThis.__pendingNav || '')")
private fun clearPendingNav() { js("globalThis.__pendingNav = null") }

/** The deterministic id of the first account the scaffold creates — the auto-signed-in demo user. */
private const val DEMO_USER_ID = "u1"

/**
 * The scenario registry — each is an LLM-authorable precondition (here, fixture-driven). Seeds are stamped with
 * [DEMO_USER_ID] so the auto-signed-in demo user (and the [ScopedTaskRepository] local-RLS filter) sees them.
 */
private fun repoForScenario(name: String): TaskRepository = when (name) {
    "empty" -> InMemoryTaskRepository()
    "many" -> InMemoryTaskRepository(
        (1..25).map { Task(id = it.toLong(), title = "Task #$it", creationDate = now(), ownerId = DEMO_USER_ID) },
    )
    "error" -> FailingTaskRepository()
    else -> InMemoryTaskRepository(defaultSeed())
}

private fun defaultSeed() = listOf(
    Task(id = 1, title = "Buy oat milk", creationDate = now(), ownerId = DEMO_USER_ID),
    Task(id = 2, title = "Ship the minimap prototype", creationDate = now(), ownerId = DEMO_USER_ID),
    Task(id = 3, title = "Water plants", isRepeating = true, alarmInterval = AlarmInterval.WEEKLY, creationDate = now(), ownerId = DEMO_USER_ID),
)

private fun scenarioParam(): String =
    Regex("scenario=([^&]+)").find(window.location.search)?.groupValues?.get(1) ?: "default"

private fun publishScenario(name: String) {
    js("globalThis.__scenario = name")
}

private fun now(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
