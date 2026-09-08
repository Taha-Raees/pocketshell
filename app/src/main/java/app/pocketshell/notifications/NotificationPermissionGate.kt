package app.pocketshell.notifications

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext

/**
 * M7.2 P1 — the runtime POST_NOTIFICATIONS request gate, mounted once at the
 * composition root.
 *
 * Behavior (all decisions go through the pure, unit-tested
 * [NotificationPermissionPolicy]):
 *
 *  - silently does nothing pre-Android 13, when already granted, when this
 *    install already spent its one controlled request, or when the system's
 *    own implicit prompt was already denied;
 *  - fires exactly once per install, and only when the first terminal
 *    session exists — the moment notifications become meaningful (the FGS
 *    retention notification starts with the first session);
 *  - marks the request spent BEFORE launching the dialog, so rotation,
 *    recreation or process death mid-dialog can never re-arm it;
 *  - treats granted / denied / dismissed identically afterwards: no crash,
 *    no re-ask, terminal functionality completely unaffected.
 *
 * The `initialValue = true` on the flag flow keeps the gate silent during
 * the brief DataStore load (never ask before the persisted state arrives).
 */
@Composable
fun NotificationPermissionGate(hasSessions: Boolean) {
    val context = LocalContext.current
    val activity = context as? Activity
    val preferences = remember { NotificationPreferences(context.applicationContext) }
    val requestedBefore by preferences.permissionRequested
        .collectAsStateWithLifecycle(initialValue = true)

    // The result callback is deliberately empty: the flag was already set
    // before the dialog opened, and the policy re-evaluates from real state
    // (grant status) on every composition. Nothing else to do — denial is a
    // fully supported, non-blocking outcome.
    val requestPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(hasSessions, requestedBefore) {
        val systemAlreadyAsked =
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                ?: false
        if (NotificationPermissionPolicy.shouldRequest(
                sdkInt = Build.VERSION.SDK_INT,
                granted = isPostNotificationsGranted(context),
                requestedBefore = requestedBefore,
                systemAlreadyAsked = systemAlreadyAsked,
            )
        ) {
            // Pessimistic first: the ask is spent whether the user answers
            // it, dismisses it, or the process dies under it.
            preferences.markPermissionRequested()
            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private fun isPostNotificationsGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
