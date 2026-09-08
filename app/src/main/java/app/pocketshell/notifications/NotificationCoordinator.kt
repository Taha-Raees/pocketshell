package app.pocketshell.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import app.pocketshell.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * M7.2 P1 — the ONE owner of the notification foundation's output layer:
 * event channels, permission-aware posting, deterministic identity, safe
 * content intents, and the startup stale-notification sweep.
 *
 * Hard boundaries (P1 spec §6 / P0 audit §9-§10 — the single-source rule):
 * the coordinator does NOT own terminal sessions, does NOT own agent state,
 * does NOT scan /proc, does NOT inspect terminal text, does NOT infer
 * completion or waiting-for-input, and never decides whether a session
 * exists. It is an output/integration layer: P2+ phases feed it factual
 * state transitions from TerminalSessionManager (via future projections),
 * and it turns them into system notifications — nothing more.
 *
 * Channel ownership (P1 spec §5): TerminalService keeps its
 * `terminal_sessions` foreground-service channel, untouched. This
 * coordinator owns the M7.2 activity/event channel(s) — created here, and
 * only here (idempotent; no duplicate creation logic anywhere else). Two
 * event channels exist: `session_events` (P1) and `agent_runtime` (M7.2
 * P4 — the honest agent-runtime state surfaces). Both are minimal: the
 * per-session agent-runtime notification UPDATES IN PLACE across state
 * changes, so no further channel is needed and none will be added without
 * a genuinely new event class.
 *
 * The P1 foundation itself posted NO production event notifications (there
 * were no real events to report yet). The FIRST production caller is the
 * M7.2 P4 notification consumer (AgentRuntimeNotificationConsumer), which
 * feeds P3c's deduplicated runtime transitions through [post]/[cancel];
 * using this coordinator remains the only supported way to post
 * coordinator-owned notifications, which is what makes accidental
 * un-channelled, un-identified, permission-blind posts difficult.
 */
object NotificationCoordinator {

    private const val LOG_TAG = "PocketShellNotif"

    /**
     * The M7.2 session-activity channel (P1). Kept deliberately small —
     * more channels are added by this one owner only when a real event
     * class arrives (P4's agent-runtime surfaces are exactly such a class).
     */
    const val CHANNEL_SESSION_EVENTS = "session_events"

    /**
     * The M7.2 P4 agent-runtime channel: the honest agent-runtime state
     * surfaces ("X is running" / "runtime unknown") and the one-shot
     * session-exit fact post here. One channel for the whole class — the
     * per-session surface updates in place, so polling-derived transitions
     * never spawn notification storms (Part D: minimum channels, calm).
     */
    const val CHANNEL_AGENT_RUNTIME = "agent_runtime"

    @Volatile
    private var appContext: Context? = null

    private var preferences: NotificationPreferences? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Process-scoped init — called once from PocketShellApp.onCreate.
     * Creates the event channels idempotently and runs the startup
     * stale-notification sweep (off the main thread; app start never blocks
     * on it — the m4.0.1 startup rule). Ordering is safe: sessions never
     * exist at app start (process-scoped manager, no restoration), so no
     * live event notification can race the sweep.
     */
    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (preferences == null) preferences = NotificationPreferences(app)
        ensureChannels(app)
        sweepStaleNotifications(app)
    }

    /** Idempotent channel creation — the single owner of the event channels. */
    fun ensureChannels(context: Context) {
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SESSION_EVENTS,
                "Session activity",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notifications about terminal session activity."
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_AGENT_RUNTIME,
                "Agent activity",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description =
                    "Notifications about agent runtime state in terminal sessions."
            }
        )
    }

    /**
     * Whether this app may currently post notifications to the user
     * (Android 13+ POST_NOTIFICATIONS grant, or the pre-13 app-level
     * notification toggle). Callers get honest failure from [post] even
     * without checking; this is for UI/policy decisions.
     */
    fun canPostNotifications(context: Context): Boolean {
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.areNotificationsEnabled()
    }

    /** A coordinator-owned event notification request. */
    data class EventNotification(
        val kind: EventKind,
        val sessionId: Long,
        val title: String,
        val text: String,
        /**
         * True for LIVE-STATE surfaces (the P4 agent-runtime state): the
         * notification represents a current state and must not be dismissed
         * by tapping (it lives until the truth changes it). False for one-shot
         * facts (the default): tapping dismisses them.
         */
        val ongoing: Boolean = false,
    )

    /**
     * The closed set of event classes the coordinator can carry. Extending
     * it is a compile-time-forced decision: the identity `when` in [post]
     * must map every kind to its notification id space AND its channel
     * explicitly.
     */
    enum class EventKind { SESSION_ACTIVITY, AGENT_RUNTIME }

    /**
     * Post an event notification on the event channel. Honest no-op (false)
     * when the coordinator is not initialized or notifications are not
     * enabled — denial never breaks anything, per the P1 spec.
     *
     * The caller supplies factual content only; the coordinator derives the
     * deterministic id, ensures the channel, builds the tap intent with the
     * routing extras (P5: agent-runtime taps carry the session-targeted
     * route — navigation only, never agent rediscovery), and records the id
     * in the ledger so the next app start can sweep it if it outlived this
     * process.
     */
    fun post(request: EventNotification): Boolean {
        val context = appContext ?: return false
        ensureChannels(context)
        if (!canPostNotifications(context)) return false

        val id = when (request.kind) {
            EventKind.SESSION_ACTIVITY -> NotificationIds.sessionEvent(request.sessionId)
            EventKind.AGENT_RUNTIME -> NotificationIds.agentRuntime(request.sessionId)
        }

        val channel = when (request.kind) {
            EventKind.SESSION_ACTIVITY -> CHANNEL_SESSION_EVENTS
            EventKind.AGENT_RUNTIME -> CHANNEL_AGENT_RUNTIME
        }

        val notification: Notification = Notification.Builder(context, channel)
            .setSmallIcon(app.pocketshell.R.drawable.ic_launcher_foreground)
            .setContentTitle(request.title)
            .setContentText(request.text)
            .setContentIntent(contentIntent(context, id, request.kind, request.sessionId))
            // A repeated delivery of the SAME surface (an in-place state
            // update on an existing id) must never re-alert — the calm rule.
            .setOnlyAlertOnce(true)
            .setOngoing(request.ongoing)
            .setAutoCancel(!request.ongoing)
            .build()

        notificationManager(context).notify(id, notification)
        Log.d(LOG_TAG, "posted event id=$id kind=${request.kind}")

        val prefs = preferences
        if (prefs != null) scope.launch { prefs.recordActiveNotificationId(id) }
        return true
    }

    /**
     * Cancel a coordinator-owned notification (by its deterministic id) and
     * clear its ledger entry. Cancelling an id the user already dismissed is
     * a safe no-op.
     */
    fun cancel(notificationId: Int) {
        val context = appContext ?: return
        notificationManager(context).cancel(notificationId)
        val prefs = preferences
        if (prefs != null) scope.launch { prefs.clearActiveNotificationId(notificationId) }
    }

    /**
     * Startup stale-notification sweep: cancel exactly the coordinator-owned
     * event notifications recorded in the ledger, then clear it. Targeted by
     * construction — it can never touch the foreground-service notification
     * (owned by TerminalService, id 1, never in this ledger), system
     * notifications, or anything posted outside this coordinator.
     */
    private fun sweepStaleNotifications(context: Context) {
        val prefs = preferences ?: return
        scope.launch {
            val stale = prefs.activeNotificationIds.first()
            if (stale.isEmpty()) return@launch
            val manager = notificationManager(context)
            stale.forEach { id ->
                manager.cancel(id)
                prefs.clearActiveNotificationId(id)
            }
            Log.d(LOG_TAG, "stale sweep cancelled ${stale.size} notification(s)")
        }
    }

    /**
     * Content intent for a notification tap: opens MainActivity (singleTask)
     * carrying the routing extras. The notification id is the request code so
     * per-session PendingIntents stay distinct under FLAG_UPDATE_CURRENT
     * (Part I: session A's tap can never collide with session B's — the id
     * spaces are per-session deterministic).
     *
     * M7.2 P5 — the route is kind-aware (one exhaustive `when`):
     *   SESSION_ACTIVITY -> the P1 "open app" route (behavior unchanged);
     *   AGENT_RUNTIME    -> the session-targeted route carrying the SAME
     *                       authoritative session id the notification identity
     *                       already used — tap → land in that session's
     *                       terminal context (resolved against the manager's
     *                       live list at consumption time; staleness degrades
     *                       to a normal app open). Navigation only: no PID,
     *                       no /proc, no detector, no process rediscovery.
     */
    private fun contentIntent(
        context: Context,
        notificationId: Int,
        kind: EventKind,
        sessionId: Long,
    ): PendingIntent =
        PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                when (kind) {
                    EventKind.SESSION_ACTIVITY -> {
                        putExtra(NotificationRoute.EXTRA_ROUTE, NotificationRoute.ROUTE_OPEN_APP)
                    }
                    EventKind.AGENT_RUNTIME -> {
                        putExtra(NotificationRoute.EXTRA_ROUTE, NotificationRoute.ROUTE_OPEN_SESSION)
                        putExtra(NotificationRoute.EXTRA_SESSION_ID, sessionId)
                    }
                }
            },
            // MUTABLE is only needed for parity/RichPi extras — none here.
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun notificationManager(context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
