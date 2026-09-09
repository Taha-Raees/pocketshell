package app.pocketshell.notifications

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * M7.2 P7 — the waiting-evidence boundary (structural pins over the real
 * shipped sources; the established source-reading technique — the JVM suite
 * has no Robolectric/device runner, so boundaries are pinned by reading the
 * sources that ship).
 *
 * P7's mandate: a production "needs input" state may exist ONLY if a
 * trustworthy evidence source exists (bound to a live session and runtime
 * generation, not triggerable by arbitrary terminal text, with defined
 * invalidation, replay/dedup safety, no screenshot polling, no inactivity
 * inference). The P7 audit (docs/M7.2-P7-WAITING-EVIDENCE-AUDIT.md)
 * concluded that NO channel in this architecture can carry that evidence
 * today, so P7 ships NO production state and NO new claim — it pins the
 * boundary the audit established, so the conclusion cannot silently drift:
 *
 *   1. THE ATTENTION SEAMS ARE DECORATIVE — the emulator's attention-capable
 *      callbacks (BEL, title changes, screen updates) terminate inside the
 *      session client at repaint, title storage and logging; the client has
 *      NO path into the detector, the event engine, the runtime event
 *      vocabulary, the repository or the notification stack. A `printf '\a'`
 *      or an arbitrary title escape can never mint a runtime claim.
 *   2. NO SCREEN SCRAPING — no app-layer source reads the rendered terminal
 *      text: TerminalBuffer / getSelectedText / getTranscriptText /
 *      getWordAtLocation are unreachable from app.pocketshell. Terminal text
 *      is display and selection only, never state input — so a
 *      `printf 'Allow? [Y/n]'` cannot become evidence, because nothing reads
 *      it.
 *   3. THE RUNTIME SURFACE WATCHES NO FILES AND READS NO TERMINAL TEXT —
 *      the detector, the event engine, the repository and the notification
 *      files contain no FileObserver/ContentObserver side channels; the only
 *      evidence input remains the typed /proc observation (P3b's frozen
 *      contract).
 *   4. THE NOTIFICATION LAYER CANNOT WRITE TO THE TERMINAL — the
 *      notification files reference no TerminalSession and no write/paste
 *      path: a notification can navigate (P5 tap routing) but can never
 *      answer a prompt. Tap-to-terminal remains the only interaction
 *      (Part G's response-action boundary).
 *
 * These are conclusion pins, not capability tests: they hold the audit's
 * negative result. A future phase that legitimately introduces trusted
 * attention evidence must consciously revise them (and the audit document),
 * not tiptoe around them.
 */
class AgentRuntimeWaitingEvidenceBoundaryTest {

    // ---------------------------------------------------------- helpers

    private fun findAppSourceRoot(): File? =
        listOf(
            File("src/main/java"),
            File("app/src/main/java"),
            File("../app/src/main/java"),
        ).firstOrNull { it.isDirectory }

    private fun findFile(relativeUnderApp: String): File? {
        val root = findAppSourceRoot() ?: return null
        val direct = File(root, relativeUnderApp)
        if (direct.isFile) return direct
        // fall back to a repo-wide walk for nested package paths
        return root.walkTopDown()
            .filter { it.isFile && it.name == relativeUnderApp.substringAfterLast('/') }
            .firstOrNull { it.absolutePath.endsWith(relativeUnderApp) }
            ?: root.walkTopDown()
                .firstOrNull { it.isFile && it.absolutePath.endsWith(relativeUnderApp) }
    }

    private fun readSourceOrSkip(relativeUnderApp: String): String? {
        val file = findFile(relativeUnderApp)
        org.junit.Assume.assumeTrue("source not found on this runner: $relativeUnderApp", file != null)
        return file!!.readText()
    }

    /** Strip // and /* */ comments (code view; string contents preserved). */
    private fun stripComments(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        while (i < source.length) {
            val c = source[i]
            when {
                c == '/' && i + 1 < source.length && source[i + 1] == '*' -> {
                    i = source.indexOf("*/", i + 2).let { if (it < 0) source.length else it + 2 }
                }
                c == '/' && i + 1 < source.length && source[i + 1] == '/' -> {
                    while (i < source.length && source[i] != '\n') i++
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }

    private fun assertNone(code: String, banned: List<String>, scope: String) {
        for (token in banned) {
            assertTrue(
                "$scope must contain no '$token' (waiting-evidence boundary, P7 audit)",
                !code.contains(token),
            )
        }
    }

    // ---------------------------------- 1: the attention seams are decorative

    @Test
    fun `session client attention seams terminate at repaint title and logging - no runtime path`() {
        val source = readSourceOrSkip(
            "java/app/pocketshell/terminal/PocketShellSessionClient.kt",
        ) ?: return
        val code = stripComments(source)
        // Positive pins: the seams exist where the audit says they are.
        for (seam in listOf("onBell", "onTitleChanged", "onTextChanged")) {
            assertTrue(
                "the session client must still own the $seam seam (audit scope drifted)",
                seam in code,
            )
        }
        // Negative pin: none of them may reach the evidence machinery.
        assertNone(
            code,
            listOf(
                "RuntimeAgentDetector",
                "AgentRuntimeEventEngine",
                "AgentRuntimeEvents",
                "AgentActivityRepository",
                "NotificationCoordinator",
                "app.pocketshell.notifications",
            ),
            "the session client (BEL/title/screen seams)",
        )
    }

    // ------------------------------------------------ 2: no screen scraping

    @Test
    fun `no app-layer source reads the rendered terminal text - no screen scraping`() {
        val root = findAppSourceRoot()
        org.junit.Assume.assumeTrue("app source root not found on this runner", root != null)
        val banned = listOf(
            "getSelectedText",
            "getTranscriptText",
            "getWordAtLocation",
            "TerminalBuffer",
        )
        val offenders = mutableListOf<String>()
        root!!.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val code = stripComments(file.readText())
                for (token in banned) {
                    if (token in code) offenders.add("${file.name}: $token")
                }
            }
        assertTrue(
            "app-layer sources must never read rendered terminal text " +
                "(screen scraping is the forbidden waiting heuristic): $offenders",
            offenders.isEmpty(),
        )
    }

    // ------------------------- 3: no file-watching / text side channels

    @Test
    fun `the runtime surface watches no files and reads no terminal text`() {
        val surface = listOf(
            "java/app/pocketshell/terminal/RuntimeAgentDetector.kt",
            "java/app/pocketshell/terminal/AgentRuntimeDetection.kt",
            "java/app/pocketshell/terminal/AgentRuntimeEvents.kt",
            "java/app/pocketshell/terminal/AgentRuntimeEventEngine.kt",
            "java/app/pocketshell/terminal/AgentActivityRepository.kt",
            "java/app/pocketshell/notifications/AgentRuntimeNotificationConsumer.kt",
            "java/app/pocketshell/notifications/AgentRuntimeNotificationMapping.kt",
            "java/app/pocketshell/notifications/NotificationCoordinator.kt",
            "java/app/pocketshell/notifications/NotificationRoute.kt",
            "java/app/pocketshell/notifications/AgentRuntimeNotificationRouting.kt",
            "java/app/pocketshell/notifications/NotificationIds.kt",
        )
        val banned = listOf(
            "FileObserver",
            "ContentObserver",
            "getSelectedText",
            "getTranscriptText",
            "getWordAtLocation",
        )
        for (relative in surface) {
            val source = readSourceOrSkip(relative) ?: continue
            assertNone(stripComments(source), banned, relative.substringAfterLast('/'))
        }
    }

    // ------------- 4: the notification layer cannot write to the terminal

    @Test
    fun `notification layer cannot write to the terminal - tap-to-terminal stays the only interaction`() {
        val notifications = listOf(
            "java/app/pocketshell/notifications/AgentRuntimeNotificationConsumer.kt",
            "java/app/pocketshell/notifications/AgentRuntimeNotificationMapping.kt",
            "java/app/pocketshell/notifications/NotificationCoordinator.kt",
            "java/app/pocketshell/notifications/NotificationRoute.kt",
            "java/app/pocketshell/notifications/AgentRuntimeNotificationRouting.kt",
            "java/app/pocketshell/notifications/NotificationIds.kt",
        )
        val banned = listOf(
            "TerminalSession",
            ".write(",
            "writeCodePoint",
            "paste(",
        )
        for (relative in notifications) {
            val source = readSourceOrSkip(relative) ?: continue
            assertNone(stripComments(source), banned, relative.substringAfterLast('/'))
        }
    }
}
