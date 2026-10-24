package dev.flip2dnd.next.core

import java.util.Calendar
import java.util.GregorianCalendar
import org.junit.Assert.*
import org.junit.Test

class ScheduleWindowTest {
    private fun monday(hour: Int, minute: Int = 0): Calendar =
        GregorianCalendar(2026, Calendar.OCTOBER, 19, hour, minute)

    @Test fun daytimeIncludesStartAndExcludesEnd() {
        val days = setOf(Calendar.MONDAY)
        assertTrue(ScheduleWindow.contains("09:00", "17:00", days, monday(9)))
        assertFalse(ScheduleWindow.contains("09:00", "17:00", days, monday(17)))
    }
    @Test fun unselectedDaysDoNotActivate() {
        assertFalse(ScheduleWindow.contains("09:00", "17:00", setOf(Calendar.SUNDAY), monday(12)))
    }
    @Test fun overnightUsesTheDayWhenTheWindowStarted() {
        val tue = monday(2).apply { add(Calendar.DAY_OF_MONTH, 1) }
        assertTrue(ScheduleWindow.contains("22:00", "07:00", setOf(Calendar.MONDAY), tue))
        assertFalse(ScheduleWindow.contains("22:00", "07:00", setOf(Calendar.TUESDAY), tue))
        assertTrue(ScheduleWindow.contains("22:00", "07:00", setOf(Calendar.MONDAY), monday(23)))
        assertFalse(ScheduleWindow.contains("22:00", "07:00", setOf(Calendar.MONDAY), monday(12)))
    }
    @Test fun overnightWrapsSaturdayToSunday() {
        val sunday = monday(2).apply { add(Calendar.DAY_OF_MONTH, -1) }
        assertTrue(ScheduleWindow.contains("22:00", "07:00", setOf(Calendar.SATURDAY), sunday))
    }
    @Test fun identicalTimesMeanWholeSelectedDay() {
        assertTrue(ScheduleWindow.contains("09:00", "09:00", setOf(Calendar.MONDAY), monday(3)))
    }
    @Test fun malformedTimesAndEmptyDaysFailClosed() {
        for (time in listOf("bad", "25:00", "10:60", "10:00:00")) {
            assertFalse(ScheduleWindow.contains(time, "17:00", setOf(Calendar.MONDAY), monday(12)))
        }
        assertFalse(ScheduleWindow.contains("09:00", "17:00", emptySet(), monday(12)))
    }
}
