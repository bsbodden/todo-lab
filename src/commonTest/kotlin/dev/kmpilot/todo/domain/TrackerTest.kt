package dev.kmpilot.todo.domain

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackerTest {
    private val utc = TimeZone.UTC
    private val now = LocalDateTime.parse("2026-06-30T12:00")
    // explicit completedDate fixtures (no arithmetic in the test)
    private fun done(id: Long, date: String, cat: Long?) =
        Task(id = id, title = "t$id", isCompleted = true, categoryId = cat,
            completedDate = LocalDateTime.parse(date))

    @Test fun counts_only_in_window_completed_tasks_and_groups_by_category() {
        val tasks = listOf(
            done(1, "2026-06-15T09:00", cat = 10),   // 15 days ago -> IN
            done(2, "2026-06-20T09:00", cat = 10),   // IN
            done(3, "2026-06-25T09:00", cat = null),  // IN, uncategorized
            done(4, "2026-03-01T09:00", cat = 10),   // ~90 days ago -> OUT
            Task(id = 5, title = "t5", isCompleted = false, categoryId = 10), // incomplete -> OUT
        )
        val stats = Tracker.stats(tasks, now, utc)
        // 3 in window: cat 10 -> 2, null -> 1
        assertEquals(3, stats.sumOf { it.count })
        assertEquals(2, stats.first { it.categoryId == 10L }.count)
        assertEquals(1, stats.first { it.categoryId == null }.count)
    }

    @Test fun percentages_sum_to_one() {
        val tasks = listOf(
            done(1, "2026-06-15T09:00", cat = 10),
            done(2, "2026-06-20T09:00", cat = 20),
            done(3, "2026-06-25T09:00", cat = 20),
        )
        val stats = Tracker.stats(tasks, now, utc)
        assertEquals(1.0f, stats.map { it.percentage }.sum(), absoluteTolerance = 1e-5f)
        assertEquals(2f / 3f, stats.first { it.categoryId == 20L }.percentage, absoluteTolerance = 1e-5f)
    }

    @Test fun empty_window_returns_no_stats() {
        val tasks = listOf(done(1, "2026-01-01T09:00", cat = 10)) // way outside
        assertTrue(Tracker.stats(tasks, now, utc).isEmpty())
    }
}
