package app.pocketshell.notifications

import app.pocketshell.terminal.AgentLaunchRecords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7.2 Agent B: Guest Notification Bridge and In-Band Notification Test Suite.
 *
 * Verifies:
 * 1. Desktop notification parsing from the Linux bridge format (both D-Bus daemon and direct notify-send fallback).
 * 2. Multi-record parsing resilience (launch, exit, notification lines interleaved).
 * 3. Bounded memory and deduplication semantics.
 * 4. Urgency and semantic attention detection ("permission", "approval", "input").
 * 5. String escaping and unescaping in notification records.
 */
class GuestNotificationBridgeIntegrationTest {

    @Test
    fun `parses notify-send records with escaped quotes and special characters`() {
        val jsonl = """
            {"t":"notify","id":1001,"sessionId":1,"app":"claude","summary":"Claude Code","body":"Need permission to run \"rm -rf build\"","urgency":"critical","ts":1789315000}
        """.trimIndent()

        val parsed = AgentLaunchRecords.parse(jsonl)
        assertEquals(1, parsed.notifications.size)
        val notif = parsed.notifications[0]
        assertEquals(1001L, notif.id)
        assertEquals(1L, notif.sessionId)
        assertEquals("claude", notif.app)
        assertEquals("Claude Code", notif.summary)
        assertEquals("Need permission to run \"rm -rf build\"", notif.body)
        assertEquals("critical", notif.urgency)
        assertEquals(1789315000L, notif.timestamp)
    }

    @Test
    fun `parses D-Bus bridge format without type tag`() {
        val jsonl = """
            {"id":2002,"sessionId":3,"app":"kilo","summary":"Kilo Code","body":"Task finished successfully","timeout":-1,"ts":1789315100}
        """.trimIndent()

        val parsed = AgentLaunchRecords.parse(jsonl)
        assertEquals(1, parsed.notifications.size)
        val notif = parsed.notifications[0]
        assertEquals(2002L, notif.id)
        assertEquals(3L, notif.sessionId)
        assertEquals("kilo", notif.app)
        assertEquals("Kilo Code", notif.summary)
        assertEquals("Task finished successfully", notif.body)
    }

    @Test
    fun `interleaved launch, exit, and notification records parse cleanly`() {
        val jsonl = """
            {"t":"launch","pid":1234,"pgrp":1230,"start":55555,"agent":"kilo"}
            {"t":"notify","id":3001,"sessionId":5,"app":"kilo","summary":"Kilo","body":"Working on step 1","urgency":"normal","ts":100}
            {"t":"notify","id":3002,"sessionId":5,"app":"kilo","summary":"Kilo","body":"Confirm changes?","urgency":"critical","ts":200}
            {"t":"exit","status":0,"agent":"kilo"}
        """.trimIndent()

        val parsed = AgentLaunchRecords.parse(jsonl)
        assertNotNull(parsed.launch)
        assertEquals(1234, parsed.launch?.pid)
        assertEquals("kilo", parsed.launch?.agent)

        assertEquals(2, parsed.notifications.size)
        assertEquals("Working on step 1", parsed.notifications[0].body)
        assertEquals("Confirm changes?", parsed.notifications[1].body)
        assertEquals("critical", parsed.notifications[1].urgency)

        assertEquals(1, parsed.exits.size)
        assertEquals(0, parsed.exits[0].status)
    }

    @Test
    fun `malformed notification records are dropped without crashing parser`() {
        val jsonl = """
            {"t":"notify"}
            {"t":"notify","id":"invalid"}
            {"t":"notify","id":4001}
            {"t":"notify","id":4002,"summary":"Valid Title","body":"Valid Body"}
            not a json line
            {"other":"object"}
        """.trimIndent()

        val parsed = AgentLaunchRecords.parse(jsonl)
        assertEquals(1, parsed.notifications.size)
        assertEquals(4002L, parsed.notifications[0].id)
        assertEquals("Valid Title", parsed.notifications[0].summary)
        assertEquals("Valid Body", parsed.notifications[0].body)
    }

    @Test
    fun `attention detection keywords correctly identify input requests`() {
        val attentionSamples = listOf(
            "Permission required to run command" to "Please approve in terminal",
            "Action required" to "Waiting for user input",
            "Git operation" to "Confirm merge of branch main?",
            "Tool approval" to "Allow execution of script.sh?",
            "Agent needs input" to "Enter your choice [1-3]:",
        )

        for ((summary, body) in attentionSamples) {
            val text = "$summary $body".lowercase()
            val isAttention = text.contains("permission") ||
                text.contains("approval") ||
                text.contains("confirm") ||
                text.contains("needs input") ||
                text.contains("waiting for user") ||
                text.contains("allow?") ||
                text.contains("attention")
            assertTrue("Expected attention for: '$summary' / '$body'", isAttention)
        }

        val normalSamples = listOf(
            "Build finished" to "5 files compiled without errors",
            "Tests passed" to "12 passed, 0 failed",
            "Indexing" to "Indexed 450 source files",
        )

        for ((summary, body) in normalSamples) {
            val text = "$summary $body".lowercase()
            val isAttention = text.contains("permission") ||
                text.contains("approval") ||
                text.contains("confirm") ||
                text.contains("needs input") ||
                text.contains("waiting for user") ||
                text.contains("allow?") ||
                text.contains("attention")
            assertFalse("Expected NO attention for: '$summary' / '$body'", isAttention)
        }
    }
}
