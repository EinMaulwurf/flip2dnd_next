package dev.flip2dnd.next.core

interface PowerController {
    /**
     * Toggles the system battery saver (low power mode).
     * Requires WRITE_SECURE_SETTINGS or user prompt fallback.
     */
    fun setBatterySaverEnabled(enabled: Boolean)
}
