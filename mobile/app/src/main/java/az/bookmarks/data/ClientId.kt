package az.bookmarks.data

import android.content.Context
import java.util.UUID

/**
 * Identifies this install to the backend, so one phone's bookmarks are not another's.
 *
 * This is not a login. The id is self-asserted — anyone who copies the header reads that
 * collection — so it separates collections without protecting them. Accounts are the upgrade
 * when the data is worth protecting rather than merely keeping apart, and the seam is here.
 *
 * SharedPreferences rather than DataStore: one string, read once at start-up, and DataStore
 * would bring a coroutine API and a dependency for it.
 */
object ClientId {

    private const val PREFS = "bookmarks"

    private const val KEY = "client_id"

    /**
     * The id for this install, generated on first launch and kept from then on.
     *
     * `commit()` rather than `apply()` — deliberately the blocking one. `apply()` returns before
     * the value reaches disk, and a process death in that window would hand the next launch a
     * fresh id, which reads as every bookmark having vanished. It runs once, on the first launch
     * only, on a file with one key in it.
     */
    fun of(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY, null) ?: UUID.randomUUID().toString().also { generated ->
            prefs.edit().putString(KEY, generated).commit()
        }
    }
}
