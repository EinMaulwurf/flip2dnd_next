package dev.flip2dnd.next.data.repository

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.service.notification.Condition
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowNotificationManager

/** Robolectric's built-in shadow covers rule CRUD but not the API 35 state methods. */
@Implements(NotificationManager::class, minSdk = 35)
class RuleStateShadow : ShadowNotificationManager() {
    private val states = mutableMapOf<String, Int>()
    @Implementation fun getAutomaticZenRuleState(id: String): Int = states[id] ?: Condition.STATE_FALSE
    @Implementation fun setAutomaticZenRuleState(id: String, condition: Condition) {
        states[id] = condition.state
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@Config(application = Application::class, sdk = [35], shadows = [RuleStateShadow::class])
class DeviceDndControllerTest {
    private lateinit var notifications: NotificationManager
    private lateinit var preferences: android.content.SharedPreferences
    private lateinit var controller: DeviceDndController
    private lateinit var context: Application

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(notifications).setNotificationPolicyAccessGranted(true)
        preferences = context.getSharedPreferences("flip_dnd_session", Context.MODE_PRIVATE)
        controller = DeviceDndController(context, notifications, preferences)
    }

    @Test fun restorationDeactivatesOnlyOurRuleWhileGlobalDndRemainsActive() {
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        controller.write(NotificationManager.INTERRUPTION_FILTER_NONE)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_NONE, controller.read())
        controller.write(NotificationManager.INTERRUPTION_FILTER_ALL)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, controller.read())
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, notifications.currentInterruptionFilter)
    }

    @Test fun recreationFindsTheSameRuleWithoutCreatingDuplicates() {
        controller.write(NotificationManager.INTERRUPTION_FILTER_NONE)
        val savedId = preferences.getString("rule_id", null)
        val recreated = DeviceDndController(context, notifications, preferences)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_NONE, recreated.read())
        recreated.write(NotificationManager.INTERRUPTION_FILTER_ALL)
        recreated.write(NotificationManager.INTERRUPTION_FILTER_ALARMS)
        assertEquals(savedId, preferences.getString("rule_id", null))
        assertEquals(1, notifications.automaticZenRules.size)
    }

    @Test fun disabledRuleIsNotMistakenForOwnedSilence() {
        controller.write(NotificationManager.INTERRUPTION_FILTER_NONE)
        val id = preferences.getString("rule_id", null)!!
        val rule = notifications.getAutomaticZenRule(id)!!
        rule.isEnabled = false
        notifications.updateAutomaticZenRule(id, rule)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, controller.read())
    }

    @Test fun endingAnOverriddenCycleResetsTheAutomaticCondition() {
        controller.write(NotificationManager.INTERRUPTION_FILTER_NONE)
        controller.endAutomaticCycle()
        val id = preferences.getString("rule_id", null)!!
        assertEquals(Condition.STATE_FALSE, notifications.getAutomaticZenRuleState(id))
        controller.write(NotificationManager.INTERRUPTION_FILTER_NONE)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_NONE, controller.read())
    }
}
