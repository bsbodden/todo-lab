package dev.kmpilot.todo.presentation

import dev.kmpilot.todo.domain.Task
import dev.kmpilot.todo.runtime.ChartSpec
import dev.kmpilot.todo.runtime.StateSpec
import dev.kmpilot.todo.runtime.TransitionSpec
import dev.kmpilot.todo.runtime.publishChartSpec
import dev.kmpilot.todo.runtime.publishScreenState
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.nsk.kstatemachine.event.*
import ru.nsk.kstatemachine.state.*
import ru.nsk.kstatemachine.statemachine.*
import ru.nsk.kstatemachine.transition.*

/** The rendered states of the task-list screen — projected 1:1 from the statechart's active state. */
sealed interface TaskListUi {
    data object Loading : TaskListUi
    data class Content(val tasks: List<Task>) : TaskListUi
    data object Empty : TaskListUi
    data class Error(val message: String) : TaskListUi
}

/**
 * The TaskList screen as a STATECHART — the first `ScreenMachine`. Every UI interaction is an [Event]; the
 * machine is the SOLE state mutator; the active state projects to [ui] (a `StateFlow` the Compose screen
 * renders). This makes the screen's behavior one testable, diagrammable model: the canonical async
 * sub-states (Loading → Content / Empty / Error) are explicit, illegal transitions are no-ops, and the same
 * model drives the runtime, the tests, and (next) the minimap. Runtime: KStateMachine 0.38.1.
 */
class TaskListMachine(private val scope: CoroutineScope) {

    // Events = the interactions/inputs. Each binds to a UI gesture (for device-level model-based testing).
    data class Loaded(val tasks: List<Task>) : Event
    data class LoadFailed(val message: String) : Event
    object Retry : Event

    companion object {
        /** The owned ChartSpec for this screen — co-located with the state/transition definitions below, so the
         *  minimap renders the REAL machine's structure (published at runtime), not a hand-authored studio file. */
        val CHART = ChartSpec(
            id = "TaskList",
            initial = "Loading",
            states = listOf(
                StateSpec("Loading", "loading"), StateSpec("Content", "content"),
                StateSpec("Empty", "empty"), StateSpec("Error", "error"),
            ),
            transitions = listOf(
                TransitionSpec("Loading", "Content", "Loaded[items]"),
                TransitionSpec("Loading", "Empty", "Loaded[none]"),
                TransitionSpec("Loading", "Error", "LoadFailed"),
                TransitionSpec("Content", "Content", "Loaded[items]"),
                TransitionSpec("Content", "Empty", "Loaded[none]"),
                TransitionSpec("Empty", "Content", "Loaded[items]"),
                TransitionSpec("Empty", "Empty", "Loaded[none]"),
                TransitionSpec("Error", "Loading", "Retry"),
            ),
        )
    }

    private val _ui = MutableStateFlow<TaskListUi>(TaskListUi.Loading)
    val ui: StateFlow<TaskListUi> = _ui.asStateFlow()

    private lateinit var machine: StateMachine

    private fun project(tasks: List<Task>): TaskListUi =
        if (tasks.isEmpty()) TaskListUi.Empty else TaskListUi.Content(tasks)

    /** Single sink for state changes: update the StateFlow AND publish the logical state to the host. */
    private fun emit(ui: TaskListUi) {
        _ui.value = ui
        publishScreenState("TaskList", describe(ui))
    }

    private fun describe(ui: TaskListUi): String = when (ui) {
        is TaskListUi.Loading -> "Loading"
        is TaskListUi.Content -> "Content:${ui.tasks.size}"
        is TaskListUi.Empty -> "Empty"
        is TaskListUi.Error -> "Error:${ui.message}"
    }

    suspend fun start() {
        machine = createStateMachine(scope, name = "TaskList") {
            val loading = initialState("Loading")
            val content = state("Content")
            val empty = state("Empty")
            val error = state("Error")

            // Loaded → Content/Empty (guarded on emptiness). The projection is updated in onTriggered (which
            // fires on EVERY transition, including Content→Content self-transitions) — NOT onEntry, which is
            // skipped on self-transitions and was the bug that froze the list after the first load.
            content {
                transitionConditionally<Loaded> {
                    direction = { if (event.tasks.isEmpty()) targetState(empty) else targetState(content) }
                    onTriggered { emit(project(it.event.tasks)) }
                }
            }
            empty {
                transitionConditionally<Loaded> {
                    direction = { if (event.tasks.isEmpty()) targetState(empty) else targetState(content) }
                    onTriggered { emit(project(it.event.tasks)) }
                }
            }
            loading {
                transitionConditionally<Loaded> {
                    direction = { if (event.tasks.isEmpty()) targetState(empty) else targetState(content) }
                    onTriggered { emit(project(it.event.tasks)) }
                }
                transition<LoadFailed> {
                    onTriggered { emit(TaskListUi.Error(it.event.message)) }
                    targetState = error
                }
            }
            error {
                transition<Retry> {
                    onTriggered { emit(TaskListUi.Loading) }
                    targetState = loading
                }
            }
        }
        publishChartSpec(Json.encodeToString(CHART))   // the structure (for the minimap/editor)
        publishScreenState("TaskList", "Loading")        // initial active state
    }

    suspend fun onLoaded(tasks: List<Task>) { machine.processEvent(Loaded(tasks)) }
    suspend fun onLoadFailed(message: String) { machine.processEvent(LoadFailed(message)) }
    suspend fun retry() { machine.processEvent(Retry) }
}
