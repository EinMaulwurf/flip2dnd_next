package dev.flip2dnd.next.data.repository

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import androidx.annotation.RequiresApi
import dev.flip2dnd.next.MainActivity
import dev.flip2dnd.next.R

/** Android 15 scopes DND to an app rule. Never infer that rule's state from global DND. */
internal class DeviceDndController(
    private val context: Context,
    private val notifications: NotificationManager,
    private val preferences: SharedPreferences,
) {
    private val conditionId = Uri.parse("condition://${context.packageName}/face-down")

    fun read(): Int {
        if (Build.VERSION.SDK_INT < 35) return notifications.currentInterruptionFilter
        if (!notifications.isNotificationPolicyAccessGranted) return NotificationManager.INTERRUPTION_FILTER_ALL
        val id = preferences.getString("rule_id", null) ?: return NotificationManager.INTERRUPTION_FILTER_ALL
        val rule = notifications.getAutomaticZenRule(id) ?: return NotificationManager.INTERRUPTION_FILTER_ALL
        return if (rule.isEnabled && notifications.getAutomaticZenRuleState(id) == Condition.STATE_TRUE) {
            rule.interruptionFilter
        } else NotificationManager.INTERRUPTION_FILTER_ALL
    }

    fun endAutomaticCycle() {
        if (Build.VERSION.SDK_INT >= 35 && notifications.isNotificationPolicyAccessGranted) {
            writeRule(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }

    fun write(value: Int) {
        check(notifications.isNotificationPolicyAccessGranted) { "Notification policy access is required" }
        if (Build.VERSION.SDK_INT < 35) {
            notifications.setInterruptionFilter(value)
            return
        }
        writeRule(value)
    }

    @RequiresApi(35)
    private fun writeRule(value: Int) {
        val savedId = preferences.getString("rule_id", null)
        val savedRule = savedId?.let { notifications.getAutomaticZenRule(it) }
        if (value == NotificationManager.INTERRUPTION_FILTER_ALL) {
            if (savedId != null && savedRule != null) {
                notifications.setAutomaticZenRuleState(savedId,
                    Condition(conditionId, context.getString(R.string.app_name), Condition.STATE_FALSE, Condition.SOURCE_CONTEXT))
            }
            return
        }
        val rule = AutomaticZenRule.Builder(context.getString(R.string.app_name), conditionId)
            .setConfigurationActivity(ComponentName(context, MainActivity::class.java))
            .setInterruptionFilter(value)
            .setEnabled(true)
            .build()
        val id = if (savedRule != null && savedId != null) {
            check(notifications.updateAutomaticZenRule(savedId, rule)) { "Cannot update DND rule" }
            savedId
        } else {
            notifications.addAutomaticZenRule(rule).also { created ->
                check(preferences.edit().putString("rule_id", created).commit()) { "Cannot save DND rule" }
            }
        }
        notifications.setAutomaticZenRuleState(id,
            Condition(conditionId, context.getString(R.string.app_name), Condition.STATE_TRUE, Condition.SOURCE_CONTEXT))
    }
}
