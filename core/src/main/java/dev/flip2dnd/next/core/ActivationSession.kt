package dev.flip2dnd.next.core

/** A device-local record of exactly what we changed, independent of current settings. */
data class ActivationSession(val mode: ActivationMode, val previous: Int, val applied: Int)

/** Callers serialize access. Hardware reads must describe our rule on modern Android,
 * rather than the effective DND result of all applications combined. */
class ActivationSessionController(
    private val hardware: Hardware,
    private val store: Store,
) {
    interface Hardware {
        fun read(mode: ActivationMode): Int
        fun write(mode: ActivationMode, value: Int)
    }
    interface Store {
        fun load(): ActivationSession?
        fun save(session: ActivationSession?)
    }

    var session: ActivationSession? = store.load()
        private set

    fun reconcile() {
        val current = session ?: return
        // A user's newer choice takes precedence over restoring the old state.
        if (hardware.read(current.mode) != current.applied) save(null)
    }

    fun activate(mode: ActivationMode, value: Int): Boolean {
        reconcile()
        if (session?.mode == mode) return false
        restore()
        val previous = hardware.read(mode)
        if (previous == value) return false // Never claim a state we didn't change.
        // Persist before the system call, so process death cannot lose the restoration data.
        save(ActivationSession(mode, previous, value))
        hardware.write(mode, value)
        return true
    }

    fun restore(): Boolean {
        reconcile()
        val current = session ?: return false
        hardware.write(current.mode, current.previous)
        save(null)
        return true
    }

    private fun save(value: ActivationSession?) {
        store.save(value)
        session = value
    }
}
