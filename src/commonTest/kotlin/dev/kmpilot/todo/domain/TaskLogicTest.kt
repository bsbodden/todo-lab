package dev.kmpilot.todo.domain

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskLogicTest {
    private val now = LocalDateTime.parse("2026-06-01T12:00")
    private fun task(id: Long, title: String, completed: Boolean = false, cat: Long? = null) =
        Task(id = id, title = title, isCompleted = completed, categoryId = cat)

    @Test fun complete_sets_completedDate_and_uncomplete_clears_it() {
        val t = task(1, "Pay rent")
        val done = TaskLogic.complete(t, now)
        assertTrue(done.isCompleted); assertEquals(now, done.completedDate)
        val undone = TaskLogic.uncomplete(done)
        assertFalse(undone.isCompleted); assertNull(undone.completedDate)
        // toggle flips based on current state
        assertTrue(TaskLogic.toggle(t, now).isCompleted)
        assertFalse(TaskLogic.toggle(done, now).isCompleted)
    }

    @Test fun uncompleted_hides_completed_and_filters_by_category() {
        val tasks = listOf(
            task(1, "a", cat = 10), task(2, "b", completed = true, cat = 10), task(3, "c", cat = 20),
        )
        assertEquals(listOf(1L, 3L), TaskLogic.uncompleted(tasks).map { it.id })
        assertEquals(listOf(1L), TaskLogic.uncompleted(tasks, categoryId = 10).map { it.id })
    }

    @Test fun search_is_substring_case_insensitive_empty_returns_all_ordered() {
        val tasks = listOf(
            task(1, "Birthday gift"), task(2, "Buy milk", completed = true),
            task(3, "birthday cake"), task(4, "Walk dog"),
        )
        assertEquals(setOf(1L, 3L), TaskLogic.search(tasks, "birthday").map { it.id }.toSet())
        assertEquals(4, TaskLogic.search(tasks, "").size)                 // empty -> all
        assertTrue(TaskLogic.search(tasks, "pineapple").isEmpty())        // no match -> empty
        // uncompleted before completed
        val ordered = TaskLogic.search(tasks, "")
        assertFalse(ordered.first().isCompleted)
        assertTrue(ordered.last().isCompleted)
    }

    @Test fun blank_title_is_invalid_and_interval_keeps_repeat_flag_in_lockstep() {
        assertFalse(TaskLogic.isValidNewTitle("   "))
        assertTrue(TaskLogic.isValidNewTitle("Real task"))
        val repeating = TaskLogic.setInterval(task(1, "x"), AlarmInterval.DAILY)
        assertTrue(repeating.isRepeating); assertEquals(AlarmInterval.DAILY, repeating.alarmInterval)
        val cleared = TaskLogic.setInterval(repeating, null)
        assertFalse(cleared.isRepeating); assertNull(cleared.alarmInterval)
    }
}
