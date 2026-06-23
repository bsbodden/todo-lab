package dev.kmpilot.todo.domain

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RecurrenceTest {
    private val utc = TimeZone.UTC
    private fun dt(s: String) = LocalDateTime.parse(s)

    @Test fun next_advances_by_exactly_one_interval() {
        val base = dt("2026-06-01T09:00")
        assertEquals(dt("2026-06-01T10:00"), Recurrence.nextOccurrence(base, AlarmInterval.HOURLY, utc))
        assertEquals(dt("2026-06-02T09:00"), Recurrence.nextOccurrence(base, AlarmInterval.DAILY, utc))
        assertEquals(dt("2026-06-08T09:00"), Recurrence.nextOccurrence(base, AlarmInterval.WEEKLY, utc))
        assertEquals(dt("2026-07-01T09:00"), Recurrence.nextOccurrence(base, AlarmInterval.MONTHLY, utc))
        assertEquals(dt("2027-06-01T09:00"), Recurrence.nextOccurrence(base, AlarmInterval.YEARLY, utc))
    }

    @Test fun monthly_is_calendar_aware_clamping_short_months() {
        // Jan 31 + 1 month = Feb 28 (2026 is not a leap year) — NOT +30 days.
        assertEquals(
            dt("2026-02-28T08:00"),
            Recurrence.nextOccurrence(dt("2026-01-31T08:00"), AlarmInterval.MONTHLY, utc)
        )
    }

    @Test fun catchup_advances_an_overdue_hourly_by_5h() {
        val task = Task(
            id = 1, title = "x",
            dueDate = dt("2026-06-01T09:00"), isRepeating = true, alarmInterval = AlarmInterval.HOURLY,
        )
        val now = dt("2026-06-01T14:00") // 5 hours overdue
        assertEquals(dt("2026-06-01T14:00"), Recurrence.scheduleNext(task, now, utc))
    }

    @Test fun scheduleNext_requires_all_preconditions() {
        val ok = Task(
            id = 1, title = "x",
            dueDate = dt("2026-06-01T09:00"), isRepeating = true, alarmInterval = AlarmInterval.DAILY,
        )
        val now = dt("2026-06-01T09:00")
        assertFailsWith<IllegalArgumentException> { Recurrence.scheduleNext(ok.copy(isRepeating = false), now, utc) }
        assertFailsWith<IllegalArgumentException> { Recurrence.scheduleNext(ok.copy(dueDate = null), now, utc) }
        assertFailsWith<IllegalArgumentException> { Recurrence.scheduleNext(ok.copy(alarmInterval = null), now, utc) }
    }
}
