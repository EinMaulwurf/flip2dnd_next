package dev.flip2dnd.next.core

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class ScheduleNextChangeTest {
    private fun at(day: Int, hour: Int, minute: Int = 0) = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(2026, Calendar.OCTOBER, day, hour, minute)
    }
    @Test fun nextStartAndEndFollowTheSelectedWeekday() {
        val days = setOf(Calendar.MONDAY)
        assertEquals(at(26, 22).timeInMillis, ScheduleWindow.nextChange("22:00", "07:00", days, at(25, 12)))
        assertEquals(at(27, 7).timeInMillis, ScheduleWindow.nextChange("22:00", "07:00", days, at(26, 23)))
    }
    @Test fun exactBoundarySchedulesTheFollowingChange() {
        assertEquals(at(22, 17).timeInMillis,
            ScheduleWindow.nextChange("09:00", "17:00", (1..7).toSet(), at(22, 9)))
    }
    @Test fun fullSelectedDayChangesAtMidnight() {
        assertEquals(at(27, 0).timeInMillis,
            ScheduleWindow.nextChange("09:00", "09:00", setOf(Calendar.MONDAY), at(26, 12)))
    }
    @Test fun emptyAndInvalidWindowsHaveNoAlarms() {
        assertNull(ScheduleWindow.nextChange("22:00", "07:00", emptySet(), at(22, 12)))
        assertNull(ScheduleWindow.nextChange("bad", "07:00", (1..7).toSet(), at(22, 12)))
    }
}
