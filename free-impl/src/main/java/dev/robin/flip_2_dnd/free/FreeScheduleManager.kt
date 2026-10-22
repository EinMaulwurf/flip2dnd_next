package dev.robin.flip_2_dnd.free

import android.content.Context
import dev.robin.flip_2_dnd.core.ScheduleManager
import dev.robin.flip_2_dnd.core.ScheduleWindow
import java.util.Calendar

class FreeScheduleManager(context: Context) : ScheduleManager {
    override fun isWithinSchedule(startTime: String, endTime: String, days: Set<Int>): Boolean =
        ScheduleWindow.contains(startTime, endTime, days, Calendar.getInstance())
}
