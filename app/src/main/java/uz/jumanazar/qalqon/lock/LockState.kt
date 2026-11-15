package uz.jumanazar.qalqon.lock

import java.util.Collections

/**
 * In-memory record of which protected apps are currently unlocked (PIN already
 * entered and the app still in use). Lives for the process lifetime; everything
 * re-locks when the service restarts or the user leaves the app.
 *
 * Deliberately has NO failed-attempt counter and NO penalty: wrong PINs cost
 * nothing, so kids hammering it never lock the parent out.
 */
object LockState {

    private val unlocked = Collections.synchronizedSet(HashSet<String>())

    fun unlock(pkg: String) {
        unlocked.add(pkg)
    }

    fun relock(pkg: String) {
        unlocked.remove(pkg)
    }

    fun isUnlocked(pkg: String): Boolean = unlocked.contains(pkg)

    fun clearAll() {
        unlocked.clear()
    }
}
