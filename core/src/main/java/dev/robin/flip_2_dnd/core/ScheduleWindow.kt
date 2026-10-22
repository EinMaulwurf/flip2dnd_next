package dev.robin.flip_2_dnd.core

import java.util.Calendar

/** Local-time activation window. Days use Calendar's Sunday=1 convention. */
object ScheduleWindow {
    fun contains(start: String, end: String, days: Set<Int>, now: Calendar): Boolean {
        fun minutes(value: String): Int? {
            val parts = value.split(':')
            if (parts.size != 2) return null
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null
            return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
        }
        val from = minutes(start) ?: return false
        val until = minutes(end) ?: return false
        val current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val day = now.get(Calendar.DAY_OF_WEEK)
        return when {
            from == until -> day in days // Equal times represent the whole selected day.
            from < until -> day in days && current >= from && current < until
            current >= from -> day in days
            current < until -> (if (day == Calendar.SUNDAY) Calendar.SATURDAY else day - 1) in days
            else -> false
        }
    }
}
