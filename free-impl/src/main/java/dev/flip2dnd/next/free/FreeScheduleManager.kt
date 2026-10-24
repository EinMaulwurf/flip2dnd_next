package dev.flip2dnd.next.free

import android.content.Context
import dev.flip2dnd.next.core.ScheduleManager
import dev.flip2dnd.next.core.ScheduleWindow
import java.util.Calendar

class FreeScheduleManager(context: Context) : ScheduleManager {
    override fun isWithinSchedule(startTime: String, endTime: String, days: Set<Int>): Boolean =
        ScheduleWindow.contains(startTime, endTime, days, Calendar.getInstance())
}
