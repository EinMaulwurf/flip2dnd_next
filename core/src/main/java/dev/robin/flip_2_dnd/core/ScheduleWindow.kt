package dev.robin.flip_2_dnd.core

import java.util.Calendar

/** Local-time activation window. Days use Calendar's Sunday=1 convention. */
object ScheduleWindow {
    /** First future instant at which this window changes, including overnight/day boundaries. */
    fun nextChange(start: String, end: String, days: Set<Int>, now: Calendar): Long? {
        fun parse(value: String): Int? {
            val parts = value.split(':')
            if (parts.size != 2) return null
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            return if (h in 0..23 && m in 0..59) h * 60 + m else null
        }
        val from = parse(start) ?: return null
        val until = parse(end) ?: return null
        var next: Long? = null
        for (offset in 0..8) {
            for (minute in setOf(0, from, until)) {
                val candidate = (now.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, offset)
                    set(Calendar.HOUR_OF_DAY, minute / 60)
                    set(Calendar.MINUTE, minute % 60)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val time = candidate.timeInMillis
                if (time <= now.timeInMillis) continue
                val before = (candidate.clone() as Calendar).apply { timeInMillis = time - 1 }
                if (contains(start, end, days, before) != contains(start, end, days, candidate) &&
                    (next == null || time < next)) next = time
            }
        }
        return next
    }

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
