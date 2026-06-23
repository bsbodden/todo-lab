package dev.kmpilot.todo.domain

import kotlinx.datetime.LocalDateTime

/** How a repeating task's alarm advances. */
enum class AlarmInterval { HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY }

data class Category(
    val id: Long,
    val name: String,        // required, non-blank
    val color: String,       // hex/ARGB; default Purple #7E57C2
)

/**
 * A to-do task. The (isCompleted, completedDate, isRepeating, alarmInterval) coupling is
 * load-bearing — it drives the testable domain invariants (completion, tracker window, recurrence).
 */
data class Task(
    val id: Long,
    val title: String,                       // required, non-blank
    val description: String? = null,
    val categoryId: Long? = null,            // FK -> Category (cascade -> null on delete)
    val dueDate: LocalDateTime? = null,      // the alarm/notification time
    val creationDate: LocalDateTime? = null,
    val completedDate: LocalDateTime? = null,// set on complete, cleared on uncomplete
    val isCompleted: Boolean = false,
    val isRepeating: Boolean = false,
    val alarmInterval: AlarmInterval? = null,
)
