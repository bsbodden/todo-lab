package dev.kmpilot.todo.domain

import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Pure recurrence math — the headline test-first target (mirrors Alkaa's ScheduleNextAlarmTest).
 * Calendar-aware via kotlinx-datetime, so MONTHLY/YEARLY clamp correctly and DST is honoured.
 */
object Recurrence {

    fun period(interval: AlarmInterval): DateTimePeriod = when (interval) {
        AlarmInterval.HOURLY -> DateTimePeriod(hours = 1)
        AlarmInterval.DAILY -> DateTimePeriod(days = 1)
        AlarmInterval.WEEKLY -> DateTimePeriod(days = 7)
        AlarmInterval.MONTHLY -> DateTimePeriod(months = 1)
        AlarmInterval.YEARLY -> DateTimePeriod(years = 1)
    }

    /** INV-RECUR-NEXT: advance [dueDate] by exactly one interval (calendar-aware). */
    fun nextOccurrence(dueDate: LocalDateTime, interval: AlarmInterval, tz: TimeZone): LocalDateTime =
        dueDate.toInstant(tz).plus(period(interval), tz).toLocalDateTime(tz)

    /**
     * INV-RECUR-CATCHUP: for an overdue repeating task, advance one interval at a time
     * until the occurrence is no longer before [now] (a 5h-overdue HOURLY task advances +5h).
     * INV-RECUR-PRECONDITIONS: requires isRepeating && dueDate != null && alarmInterval != null.
     */
    fun scheduleNext(task: Task, now: LocalDateTime, tz: TimeZone): LocalDateTime {
        require(task.isRepeating) { "task must be repeating" }
        val due = requireNotNull(task.dueDate) { "dueDate required" }
        val interval = requireNotNull(task.alarmInterval) { "alarmInterval required" }
        val nowInstant = now.toInstant(tz)
        var occurrence = due
        while (nowInstant > occurrence.toInstant(tz)) {
            occurrence = nextOccurrence(occurrence, interval, tz)
        }
        return occurrence
    }
}
