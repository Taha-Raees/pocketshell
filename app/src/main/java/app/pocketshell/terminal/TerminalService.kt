package app.pocketshell.terminal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import app.pocketshell.MainActivity

/**
 * Foreground service (brief §24): keeps terminal session processes alive while
 * the UI is backgrounded. Honest by construction:
 *  - runs only while at least one session exists,
 *  - notification states exactly what it does,
 *  - stops itself when the last session closes or finishes.
 */
class TerminalService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sessionCount = TerminalSessionManager.sessions.value.size
        if (sessionCount == 0) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle(getString(app.pocketshell.R.string.fgs_notification_title))
            .setContentText(
                resources.getQuantityString(
                    app.pocketshell.R.plurals.fgs_notification_sessions,
                    sessionCount,
                    sessionCount,
                )
            )
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Terminal sessions",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows when terminal sessions are running in the background."
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = "terminal_sessions"
        private const val NOTIFICATION_ID = 1

        /** Start retention service if any session exists; stop it otherwise. */
        fun syncWithSessionState(context: Context) {
            val intent = Intent(context, TerminalService::class.java)
            val running = TerminalSessionManager.sessions.value.isNotEmpty()
            if (running) {
                context.startForegroundService(intent)
            } else {
                context.stopService(intent)
            }
        }
    }
}
