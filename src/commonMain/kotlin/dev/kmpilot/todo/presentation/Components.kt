package dev.kmpilot.todo.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.value.Value
import dev.kmpilot.todo.auth.AuthPort
import dev.kmpilot.todo.data.TaskRepository
import dev.kmpilot.todo.domain.Task
import dev.kmpilot.todo.domain.TaskLogic
import dev.kmpilot.todo.runtime.ChartSpec
import dev.kmpilot.todo.runtime.StateSpec
import dev.kmpilot.todo.runtime.TransitionSpec
import dev.kmpilot.todo.runtime.publishAppGraph
import dev.kmpilot.todo.runtime.publishCurrentScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Decompose components — navigation as externalized, testable component state (the stack pick). */

class TaskListComponent(
    ctx: ComponentContext,
    private val scope: CoroutineScope,
    private val repo: TaskRepository,
    private val clock: () -> LocalDateTime,
    val onAdd: () -> Unit,
    val onOpen: (Long) -> Unit,
) : ComponentContext by ctx {
    // The screen is a STATECHART: the component feeds repo emissions in as `Loaded` events and exposes the
    // machine's projected state. The UI renders `ui` and dispatches events; the machine is the sole authority.
    private val machine = TaskListMachine(scope)
    val ui: StateFlow<TaskListUi> get() = machine.ui

    init {
        scope.launch {
            machine.start()
            repo.observeAll()
                .map { TaskLogic.uncompleted(it) }               // INV-LIST-HIDES-COMPLETED
                .catch { e -> machine.onLoadFailed(e.message ?: "Load failed") } // → Error state
                .collect { machine.onLoaded(it) }
        }
    }

    fun toggle(task: Task) { scope.launch { repo.upsert(TaskLogic.toggle(task, clock())) } } // effect → repo re-emits → Loaded
    fun retry() {
        scope.launch {
            machine.retry()
            runCatching { TaskLogic.uncompleted(repo.all()) }
                .onSuccess { machine.onLoaded(it) }
                .onFailure { machine.onLoadFailed(it.message ?: "Load failed") }
        }
    }
}

class AddTaskComponent(
    ctx: ComponentContext,
    private val scope: CoroutineScope,
    private val repo: TaskRepository,
    private val clock: () -> LocalDateTime,
    val onDone: () -> Unit,
) : ComponentContext by ctx {
    fun add(title: String) {
        if (!TaskLogic.isValidNewTitle(title)) return            // INV-ADD-REQUIRES-TITLE
        scope.launch { repo.upsert(Task(id = 0, title = title.trim(), creationDate = clock())); onDone() }
    }
    fun cancel() = onDone()
}

class TaskDetailComponent(
    ctx: ComponentContext,
    private val scope: CoroutineScope,
    private val repo: TaskRepository,
    private val taskId: Long,
    val onBack: () -> Unit,
) : ComponentContext by ctx {
    val task: StateFlow<Task?> = repo.observeAll()
        .map { list -> list.find { it.id == taskId } }
        .stateIn(scope, SharingStarted.Eagerly, null)

    fun rename(newTitle: String) {
        scope.launch { task.value?.let { repo.upsert(it.copy(title = newTitle)) } }
    }
}

class RootComponent(
    ctx: ComponentContext,
    private val scope: CoroutineScope,
    private val repo: TaskRepository,
    private val clock: () -> LocalDateTime,
    /** The identity port — exposed so [dev.kmpilot.todo.ui.App] can session-gate without touching entrypoints. */
    val auth: AuthPort,
) : ComponentContext by ctx {

    private val nav = StackNavigation<Config>()
    val stack: Value<ChildStack<Config, Child>> = childStack(
        source = nav,
        serializer = Config.serializer(),
        initialConfiguration = Config.TaskList,
        handleBackButton = true,
        childFactory = ::child,
    )

    init {
        // The top-level MINIMAP graph: screens as nodes, navigation as edges (the real app's nav).
        publishAppGraph(Json.encodeToString(APP_GRAPH))
        // Highlight/follow the active screen (so the minimap + editor track where the app is).
        stack.subscribe { childStack -> publishCurrentScreen(childStack.active.configuration.screenName()) }
    }

    companion object {
        val APP_GRAPH = ChartSpec(
            id = "App", initial = "TaskList",
            states = listOf(
                StateSpec("TaskList", "home"),
                StateSpec("AddTask", "form"),
                StateSpec("TaskDetail", "detail"),
            ),
            transitions = listOf(
                TransitionSpec("TaskList", "AddTask", "Add"),
                TransitionSpec("TaskList", "TaskDetail", "Open"),
                TransitionSpec("AddTask", "TaskList", "Save / Cancel"),
                TransitionSpec("TaskDetail", "TaskList", "Back"),
            ),
        )
    }

    private fun child(config: Config, childCtx: ComponentContext): Child = when (config) {
        Config.TaskList -> Child.TaskList(
            TaskListComponent(childCtx, scope, repo, clock,
                onAdd = { nav.pushNew(Config.AddTask) },
                onOpen = { nav.pushNew(Config.TaskDetail(it)) }),
        )
        Config.AddTask -> Child.AddTask(
            AddTaskComponent(childCtx, scope, repo, clock, onDone = { nav.pop() }),
        )
        is Config.TaskDetail -> Child.TaskDetail(
            TaskDetailComponent(childCtx, scope, repo, config.id, onBack = { nav.pop() }),
        )
    }

    @Serializable
    sealed interface Config {
        @Serializable data object TaskList : Config
        @Serializable data object AddTask : Config
        @Serializable data class TaskDetail(val id: Long) : Config
    }

    sealed interface Child {
        class TaskList(val component: TaskListComponent) : Child
        class AddTask(val component: AddTaskComponent) : Child
        class TaskDetail(val component: TaskDetailComponent) : Child
    }

    private fun Config.screenName(): String = when (this) {
        Config.TaskList -> "TaskList"
        Config.AddTask -> "AddTask"
        is Config.TaskDetail -> "TaskDetail"
    }

    /** Navigate the app to a screen by name (driven by the minimap's click-to-navigate). */
    suspend fun navigateTo(screen: String) {
        when (screen) {
            "TaskList" -> nav.replaceAll(Config.TaskList)
            "AddTask" -> nav.replaceAll(Config.TaskList, Config.AddTask)
            "TaskDetail" -> nav.replaceAll(Config.TaskList, Config.TaskDetail(repo.all().firstOrNull()?.id ?: 1L))
        }
    }
}
