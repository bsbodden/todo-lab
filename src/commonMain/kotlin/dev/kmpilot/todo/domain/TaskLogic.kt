package dev.kmpilot.todo.domain

import kotlinx.datetime.LocalDateTime

/** Pure task-domain rules — deterministic, repository-free, fully unit-testable (the test-first core). */
object TaskLogic {

    /** INV-TASK-COMPLETE: complete sets completedDate=now; uncomplete clears it; toggle flips. */
    fun complete(task: Task, now: LocalDateTime): Task =
        task.copy(isCompleted = true, completedDate = now)

    fun uncomplete(task: Task): Task =
        task.copy(isCompleted = false, completedDate = null)

    fun toggle(task: Task, now: LocalDateTime): Task =
        if (task.isCompleted) uncomplete(task) else complete(task, now)

    /** INV-LIST-HIDES-COMPLETED: only uncompleted tasks, optionally scoped to a category. */
    fun uncompleted(tasks: List<Task>, categoryId: Long? = null): List<Task> =
        tasks.filter { !it.isCompleted && (categoryId == null || it.categoryId == categoryId) }

    /** INV-SEARCH-SUBSTRING (empty query -> all) + INV-SEARCH-ORDER (uncompleted before completed). */
    fun search(tasks: List<Task>, query: String): List<Task> {
        val matched = if (query.isEmpty()) tasks
        else tasks.filter { it.title.contains(query, ignoreCase = true) }
        return matched.sortedBy { it.isCompleted } // false (uncompleted) sorts before true
    }

    /** INV-ADD-REQUIRES-TITLE: a blank title/name is not a valid new row. */
    fun isValidNewTitle(title: String): Boolean = title.isNotBlank()

    /** INV-REPEAT-FLAG: interval and isRepeating are kept in lockstep. */
    fun setInterval(task: Task, interval: AlarmInterval?): Task =
        task.copy(isRepeating = interval != null, alarmInterval = interval)
}
