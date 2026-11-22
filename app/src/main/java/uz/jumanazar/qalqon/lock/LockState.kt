package uz.jumanazar.qalqon.lock

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory record of which protected apps are currently unlocked (PIN already
 * entered). Lives for the process lifetime; everything re-locks when the service
 * restarts.
 *
 * Leaving an unlocked app does not re-lock it instantly: it gets a short grace
 * period so a quick detour (camera, gallery, file picker, permission dialog,
 * share sheet, a link that opens another app) and back does not re-ask for the
 * PIN. Going to the home screen re-locks immediately (see the service).
 *
 * Deliberately has NO failed-attempt counter and NO penalty: wrong PINs cost
 * nothing, so kids hammering it never lock the parent out.
 */
object LockState {

    /** How long an unlocked app stays unlocked after the user steps away from it. */
    const val GRACE_MS = 60_000L

    /** pkg -> time it was left (null value not allowed, so use 0 = currently in use). */
    private val unlocked = ConcurrentHashMap<String, Long>()

    fun unlock(pkg: String) {
        unlocked[pkg] = IN_USE
    }

    /** User moved away from [pkg]; start its grace period. */
    fun markLeft(pkg: String, now: Long = System.currentTimeMillis()) {
        unlocked.computeIfPresent(pkg) { _, left -> if (left == IN_USE) now else left }
    }

    /** User came back to [pkg]; if still unlocked, it is in use again. */
    fun markReturned(pkg: String) {
        unlocked.computeIfPresent(pkg) { _, _ -> IN_USE }
    }

    fun relock(pkg: String) {
        unlocked.remove(pkg)
    }

    fun isUnlocked(pkg: String, now: Long = System.currentTimeMillis()): Boolean {
        val left = unlocked[pkg] ?: return false
        if (left == IN_USE || now - left <= GRACE_MS) return true
        unlocked.remove(pkg)
        return false
    }

    fun clearAll() {
        unlocked.clear()
    }

    private const val IN_USE = 0L
}
