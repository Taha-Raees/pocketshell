package app.pocketshell.notifications

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The notifications-domain DataStore (the per-domain repository pattern used
 * by settings/ and launchers/): the permission-request bookkeeping and the
 * active-notification ledger.
 *
 * IMPORTANT — this is NOT a second session/lifecycle truth source (P1 spec
 * invariant B): it records notification DELIVERY facts only (what this
 * coordinator showed the user, and whether its one permission request was
 * spent). It never decides whether a session is running/finished — that
 * stays with TerminalSessionManager.
 */
private val Context.notificationDataStore by preferencesDataStore(name = "notifications")

class NotificationPreferences(private val context: Context) {

    private val permissionRequestedKey = booleanPreferencesKey("permission_requested")
    private val activeIdsKey = stringSetPreferencesKey("active_notification_ids")

    /**
     * True once THIS install has had its one controlled POST_NOTIFICATIONS
     * request. Written BEFORE the system dialog is launched (pessimistic):
     * a recreation or process death mid-dialog can never re-arm the request.
     */
    val permissionRequested: Flow<Boolean> =
        context.notificationDataStore.data.map { it[permissionRequestedKey] ?: false }

    suspend fun markPermissionRequested() {
        context.notificationDataStore.edit { it[permissionRequestedKey] = true }
    }

    /**
     * The ledger of coordinator-owned notification ids currently posted.
     * Entries are added by [NotificationCoordinator.post] and removed on
     * cancel; the startup sweep cancels exactly these ids and clears the
     * ledger — so after a process death no obsolete event notification
     * outlives the state that posted it.
     */
    val activeNotificationIds: Flow<Set<Int>> =
        context.notificationDataStore.data.map { prefs ->
            prefs[activeIdsKey]?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
        }

    suspend fun recordActiveNotificationId(id: Int) {
        context.notificationDataStore.edit {
            it[activeIdsKey] = (it[activeIdsKey] ?: emptySet()) + id.toString()
        }
    }

    suspend fun clearActiveNotificationId(id: Int) {
        context.notificationDataStore.edit {
            it[activeIdsKey] = (it[activeIdsKey] ?: emptySet()) - id.toString()
        }
    }

    suspend fun clearLedger() {
        context.notificationDataStore.edit { it.remove(activeIdsKey) }
    }
}
