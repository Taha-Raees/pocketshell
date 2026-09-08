package app.pocketshell.notifications

import android.util.Log
import app.pocketshell.terminal.AgentRuntimeEvent
import app.pocketshell.terminal.AgentActivityRepository
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

/**
 * M7.2 P4 — the notification CONSUMER: the layer that turns P3c's
 * deduplicated runtime transitions into honest Android notifications.
 *
 * ```text
 * AgentRuntimeEventEngine  (P3c — the ONLY event producer; dedup lives there)
 *        ↓
 * AgentActivityRepository.agentRuntimeEvents  (the re-exposed stream)
 *        ↓
 * AgentRuntimeNotificationConsumer  (this — the ONE subscriber)
 *        ↓  AgentRuntimeNotificationMapping (PURE truth contract)
 * NotificationCoordinator  (P1 — the ONE posting/identity/ledger owner)
 *        ↓
 * Android notification system
 * ```
 *
 * CONSUMER-ONLY (the mandate's hard requirements, each structural-pinned):
 * it SUBSCRIBES ONCE per process and never polls (no delay, no timers, no
 * work scheduling); it does not scan or inspect /proc, does not detect
 * processes, does not reinterpret PIDs, does not parse terminal output; it
 * owns NO lifecycle transitions and never mutates session state (it does
 * not even reference the session manager or the detector — the event types
 * are the only terminal-layer surface it touches); it infers no completion
 * and never re-labels an event.
 *
 * LIFECYCLE OWNER (Part B's mandatory question, answered by the existing
 * architecture): the Application process scope. The stream is a replay-free
 * SharedFlow — an event emitted before subscription is gone — and no
 * session can spawn before Application.onCreate returns, so the single
 * subscription armed at process start can never miss an event. A
 * service-scoped or activity-scoped owner would couple consumption to
 * service restarts / activity recreation (tearing down the collector and
 * silently missing transitions); the P1 coordinator already established the
 * Application-scoped pattern this consumer joins. No second lifecycle
 * engine exists: the consumer holds only a notification-domain [memory]
 * (what surfaces IT showed), never session truth.
 *
 * STALENESS (Part J): sessions are process-scoped and never restored, so
 * after a process death there is no runtime state to fake — the P1
 * startup sweep cancels exactly the ledgered ids, and the fresh memory
 * starts silent. Within a process, the mapping's tombstones refuse any
 * event for a session that already ended, so a late/duplicate delivery can
 * never resurrect a surface.
 *
 * Observability: one Log.d line per action (tag `PocketShellNotif`, the
 * coordinator's existing notifications-domain channel). Silence = no news.
 * No debugging UI.
 */
object AgentRuntimeNotificationConsumer {

    private const val LOG_TAG = "PocketShellNotif"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    /**
     * The notification-domain memory (Part G): what surfaces this layer has
     * shown, per session. Guarded by the ONE sequential collector below —
     * exactly like the event engine's memory discipline (one collector,
     * fold in order, no interleaving).
     */
    private var memory = AgentRuntimeNotificationMapping.Memory()

    /**
     * Subscribe exactly once per process (idempotent; safe to call from the
     * application start path). Does nothing on a second call.
     */
    fun ensureStarted() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            // The ONE subscription. The upstream stream is replay-free and
            // transition-only (P3c guarantees): every collection step IS a
            // state change, so the consumer never sees polling noise.
            AgentActivityRepository.agentRuntimeEvents.collect { event ->
                consume(event)
            }
        }
        Log.i(
            LOG_TAG,
            "agent runtime notification consumer started (subscribes to the P3c event stream; consumer-only)",
        )
    }

    /** Forcibly stop the consumer (test isolation only; production never stops it). */
    internal fun stopForTesting() {
        started.set(false)
        scope.coroutineContext.cancelChildren()
    }

    /**
     * Apply the pure truth contract to one event and execute the resulting
     * action through the coordinator. The coordinator's own gates
     * (initialization, permission, deterministic id, ledger) are the only
     * posting path — this layer never touches NotificationManager directly.
     */
    private fun consume(event: AgentRuntimeEvent) {
        val (next, action) = AgentRuntimeNotificationMapping.reduce(memory, event)
        memory = next
        when (action) {
            is AgentRuntimeNotificationMapping.Action.ShowRuntime -> {
                val posted = NotificationCoordinator.post(
                    NotificationCoordinator.EventNotification(
                        kind = NotificationCoordinator.EventKind.AGENT_RUNTIME,
                        sessionId = action.sessionId,
                        title = action.title,
                        text = action.text,
                        // A live-state surface: it stands until the truth
                        // changes it (NoLongerDetected / RuntimeUnknown /
                        // SessionEnded), not until the user taps it.
                        ongoing = true,
                    ),
                )
                Log.d(
                    LOG_TAG,
                    "runtime surface session=${action.sessionId} title=\"${action.title}\" posted=$posted",
                )
            }

            is AgentRuntimeNotificationMapping.Action.ShowExitFact -> {
                val posted = NotificationCoordinator.post(
                    NotificationCoordinator.EventNotification(
                        kind = NotificationCoordinator.EventKind.AGENT_RUNTIME,
                        sessionId = action.sessionId,
                        title = action.title,
                        text = action.text,
                        // A one-shot fact: tapping dismisses it.
                        ongoing = false,
                    ),
                )
                Log.d(
                    LOG_TAG,
                    "session exit fact session=${action.sessionId} text=\"${action.text}\" posted=$posted",
                )
            }

            is AgentRuntimeNotificationMapping.Action.Cancel -> {
                NotificationCoordinator.cancel(NotificationIds.agentRuntime(action.sessionId))
                Log.d(LOG_TAG, "runtime surface withdrawn session=${action.sessionId}")
            }

            AgentRuntimeNotificationMapping.Action.None -> Unit
        }
    }
}
