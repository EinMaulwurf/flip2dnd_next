package dev.flip2dnd.next.core

import org.junit.Assert.*
import org.junit.Test

class ActivationSessionTest {
    private class Device : ActivationSessionController.Hardware {
        var dnd = 1
        var ringer = 2
        var failWrites = false
        override fun read(mode: ActivationMode) = if (mode == ActivationMode.DND) dnd else ringer
        override fun write(mode: ActivationMode, value: Int) {
            if (failWrites) error("Permission revoked")
            if (mode == ActivationMode.DND) dnd = value else ringer = value
        }
    }
    private class Memory : ActivationSessionController.Store {
        var saved: ActivationSession? = null
        override fun load() = saved
        override fun save(session: ActivationSession?) { saved = session }
    }

    @Test fun changingModeRestoresTheOriginalMechanismBeforeActivatingTheNewOne() {
        val device = Device()
        val controller = ActivationSessionController(device, Memory())
        controller.activate(ActivationMode.RINGER, 0)
        controller.activate(ActivationMode.DND, 3)
        assertEquals(2, device.ringer)
        assertEquals(3, device.dnd)
        controller.restore()
        assertEquals(1, device.dnd)
    }

    @Test fun recreationRestoresUsingTheSavedModeAndPreviousValue() {
        val device = Device().apply { ringer = 1 }
        val store = Memory()
        ActivationSessionController(device, store).activate(ActivationMode.RINGER, 0)
        val recreated = ActivationSessionController(device, store)
        assertTrue(recreated.restore())
        assertEquals(1, device.ringer)
        assertNull(store.saved)
    }

    @Test fun manualRingerChangeRelinquishesOwnershipAndSurvivesPickup() {
        val device = Device()
        val controller = ActivationSessionController(device, Memory())
        controller.activate(ActivationMode.RINGER, 0)
        device.ringer = 1
        assertFalse(controller.restore())
        assertEquals(1, device.ringer)
        assertNull(controller.session)
    }

    @Test fun manualDndChangeIsNotOverwritten() {
        val device = Device()
        val controller = ActivationSessionController(device, Memory())
        controller.activate(ActivationMode.DND, 3)
        device.dnd = 2
        assertFalse(controller.restore())
        assertEquals(2, device.dnd)
    }

    @Test fun preexistingStateIsNeverClaimed() {
        val controller = ActivationSessionController(Device().apply { ringer = 0 }, Memory())
        assertFalse(controller.activate(ActivationMode.RINGER, 0))
        assertNull(controller.session)
    }

    @Test fun repeatedActivationDoesNotReplaceTheRestorationValue() {
        val device = Device()
        val controller = ActivationSessionController(device, Memory())
        assertTrue(controller.activate(ActivationMode.RINGER, 0))
        assertFalse(controller.activate(ActivationMode.RINGER, 0))
        controller.restore()
        assertEquals(2, device.ringer)
    }

    @Test fun failedRestorationRetainsTheSessionForRetry() {
        val device = Device()
        val store = Memory()
        val controller = ActivationSessionController(device, store)
        controller.activate(ActivationMode.DND, 3)
        device.failWrites = true
        try { controller.restore(); fail("Expected failure") } catch (_: IllegalStateException) { }
        assertNotNull(store.saved)
        device.failWrites = false
        assertTrue(controller.restore())
        assertEquals(1, device.dnd)
    }

    @Test fun failedActivationCannotLaterRestoreAnUnchangedDevice() {
        val device = Device().apply { failWrites = true }
        val store = Memory()
        val controller = ActivationSessionController(device, store)
        try { controller.activate(ActivationMode.RINGER, 0); fail("Expected failure") } catch (_: IllegalStateException) { }
        val recreated = ActivationSessionController(device, store)
        assertFalse(recreated.restore())
        assertNull(store.saved)
    }
}
