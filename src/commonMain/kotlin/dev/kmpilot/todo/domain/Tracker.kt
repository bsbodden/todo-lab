package dev.kmpilot.todo.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant

/** A per-category slice of the completion tracker. */
data class CategoryStat(val categoryId: Long?, val count: Int, val percentage: Float)

/** The 30-day completion tracker — pure aggregation (mirrors Alkaa's LoadCompletedTasksByPeriodTest). */
object Tracker {
    const val WINDOW_DAYS = 30

    /**
     * INV-TRACKER-WINDOW: count only completed tasks whose completedDate is within the last 30 days
     * (strictly > now-30d); incomplete and >30-days-ago excluded; uncategorized counted under null.
     * INV-TRACKER-PERCENT: percentage = group count / total in-window count; percentages sum to 1.0.
     */
    fun stats(tasks: List<Task>, now: LocalDateTime, tz: TimeZone): List<CategoryStat> {
        val cutoff = now.toInstant(tz).minus(WINDOW_DAYS, DateTimeUnit.DAY, tz)
        val inWindow = tasks.filter {
            it.isCompleted && it.completedDate != null && it.completedDate.toInstant(tz) > cutoff
        }
        val total = inWindow.size
        if (total == 0) return emptyList()
        return inWindow.groupBy { it.categoryId }
            .map { (categoryId, group) -> CategoryStat(categoryId, group.size, group.size.toFloat() / total) }
    }
}
