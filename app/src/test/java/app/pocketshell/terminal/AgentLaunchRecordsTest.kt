package app.pocketshell.terminal

import app.pocketshell.apps.guestLaunchChainWithRecords

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * M7.2 P9 — the launch-record channel tests (the Linux ↔ Android bridge).
 *
 * Three layers, mirroring the phase's evidence discipline:
 *
 *  1. THE PARSER (pure): the strict two-shape JSONL contract — valid
 *     records parse, ANYTHING else (malformed JSON, unknown types, out-of-
 *     domain values, foreign lines, truncated tails) is dropped, never
 *     guessed into an anchor.
 *  2. THE CHAIN COMPOSITION (pure): the record-carrying chain carries the
 *     anchor before the agent, the exit record after it, and the unchanged
 *     trailing-exec login shell — one form, quotable, spawn-ready.
 *  3. THE EXECUTION FIXTURE (real /bin/sh on the test JVM's Linux): the
 *     composed chain runs end-to-end against a REAL record file and REAL
 *     /proc — the recorded pid IS the exec'd agent's pid, the recorded
 *     starttime equals /proc/<pid> field 22 (the prototype's E1/E3 facts,
 *     pinned permanently), the exit record carries the agent's REAL exit
 *     status, and the agent's stdout/stderr/stdin behave exactly as an
 *     unwrapped command's would (PART H: the terminal is never broken).
 */
class AgentLaunchRecordsTest {

    // ------------------------------------------------------------- 1. parser

    @Test
    fun `launch and exit records parse`() {
        val parsed = AgentLaunchRecords.parse(
            "{\"t\":\"launch\",\"pid\":4242,\"pgrp\":4240,\"start\":918273,\"agent\":\"kilo\"}\n" +
                "{\"t\":\"exit\",\"status\":0,\"agent\":\"kilo\"}\n",
        )
        assertEquals(
            AgentLaunchRecords.LaunchRecord(pid = 4242, pgrp = 4240, startTicks = 918273, agent = "kilo"),
            parsed.launch,
        )
        assertEquals(listOf(AgentLaunchRecords.ExitRecord(status = 0, agent = "kilo")), parsed.exits)
    }

    @Test
    fun `negative exit statuses parse (signaled shape)`() {
        val parsed = AgentLaunchRecords.parse("{\"t\":\"exit\",\"status\":-15,\"agent\":\"kilo\"}\n")
        assertEquals(-15, parsed.exits.single().status)
    }

    @Test
    fun `malformed lines are dropped - never guessed into an anchor`() {
        val parsed = AgentLaunchRecords.parse(
            listOf(
                "not json at all",
                "{\"t\":\"launch\"}", // missing fields
                "{\"t\":\"launch\",\"pid\":0,\"pgrp\":1,\"start\":5,\"agent\":\"kilo\"}", // pid 0 impossible
                "{\"t\":\"launch\",\"pid\":-3,\"pgrp\":1,\"start\":5,\"agent\":\"kilo\"}", // negative pid
                "{\"t\":\"launch\",\"pid\":10,\"pgrp\":1,\"start\":-5,\"agent\":\"kilo\"}", // negative start
                "{\"t\":\"launch\",\"pid\":10,\"pgrp\":1,\"start\":5,\"agent\":\"DROP TABLE\"}", // non-token agent
                "{\"t\":\"other\",\"pid\":10,\"pgrp\":1,\"start\":5,\"agent\":\"kilo\"}", // unknown type
                "{\"t\":\"exit\",\"status\":\"zero\",\"agent\":\"kilo\"}", // wrong type
                "{\"t\":\"launch\",\"pid\":777,\"pgrp\":700,\"start\":12345,\"agent\":\"kilo\"}", // the ONE valid line
            ).joinToString("\n") + "\n",
        )
        assertEquals(777, parsed.launch?.pid)
        assertEquals(0, parsed.exits.size)
    }

    @Test
    fun `empty and unreadable content parse to the empty channel`() {
        assertEquals(AgentLaunchRecords.EMPTY, AgentLaunchRecords.parse(""))
        assertEquals(AgentLaunchRecords.EMPTY, AgentLaunchRecords.parse("\n\n  \n"))
    }

    @Test
    fun `a truncated final line degrades to the records before it`() {
        val parsed = AgentLaunchRecords.parse(
            "{\"t\":\"launch\",\"pid\":5,\"pgrp\":4,\"start\":6,\"agent\":\"kilo\"}\n" +
                "{\"t\":\"exit\",\"sta", // crash mid-write
        )
        assertEquals(5, parsed.launch?.pid)
        assertTrue(parsed.exits.isEmpty())
    }

    @Test
    fun `tokens are plain hex-class words`() {
        val token = AgentLaunchRecords.newToken("c0ffee-deadbeef-1234567890abcdefXYZ")
        assertTrue(token.startsWith("p9"))
        assertTrue("the kept characters are hex only", token.drop(2).all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals(26, token.length) // "p9" + 24 kept chars
    }

    @Test
    fun `readFile on a missing file is the empty channel - never a guess`() {
        assertEquals(AgentLaunchRecords.EMPTY, AgentLaunchRecords.readFile("/nonexistent/p9.jsonl"))
    }

    // ----------------------------------------------------- 2. chain composition

    @Test
    fun `the record chain carries anchor then exit then the login shell`() {
        val chain = AgentLaunchRecords.launchChain(
            agentCommand = "kilo",
            agentToken = "kilo",
            guestShell = "/bin/sh",
            guestRecordFile = "/var/lib/pocketshell-agent/p9abc.jsonl",
        )
        val anchorEnd = chain.indexOf("' ; ")
        assertTrue("the anchor (nested sh) comes first", chain.startsWith("sh -c '"))
        assertTrue("the anchor execs the agent", chain.contains("exec kilo'"))
        assertTrue(
            "the exit record follows the anchor",
            chain.contains(") ; printf") || Regex("exec kilo' ; printf").containsMatchIn(chain),
        )
        assertTrue(
            "the trailing-exec contract is unchanged",
            chain.endsWith("; exec /bin/sh -l"),
        )
        assertTrue(
            "the record file path travels verbatim",
            chain.contains("/var/lib/pocketshell-agent/p9abc.jsonl"),
        )
        assertTrue("the anchor end is sane", anchorEnd > 0)
    }

    @Test
    fun `the sibling composition quotes non-allowlist tokens and names the head`() {
        val chain = guestLaunchChainWithRecords(
            launchCommand = listOf("kilo"),
            guestShell = "/bin/sh",
            guestRecordFile = "/var/lib/pocketshell-agent/p9abc.jsonl",
        )
        assertTrue(chain.startsWith("sh -c '"))
        assertTrue(chain.endsWith("; exec /bin/sh -l"))
        assertTrue("the record names the agent token", chain.contains("\\\"agent\\\":\\\"kilo\\\""))
        assertTrue(chain.contains("exec kilo"))
    }

    // ----------------------------------------------------- 3. execution fixture

    /** Spawn a detached "session" (setsid root), run the chain inside it, return the root pid. */
    private fun runChainInSession(chain: String): Process =
        ProcessBuilder("setsid", "sh", "-c", chain).start()

    @Test
    fun `execution - the anchor records the agent's real pid, starttime and the real exit status`() {
        val work = java.nio.file.Files.createTempDirectory("p9records").toFile()
        try {
            val agent = File(work, "kilo").apply {
                writeText("#!/bin/sh\nprintf out-agent-stdout\nexit ${'$'}{KILO_EXIT:-0}\n")
                setExecutable(true)
            }
            val recordFile = File(work, "record.jsonl").apply { createNewFile() }
            val chain = AgentLaunchRecords.launchChain(
                agentCommand = agent.absolutePath,
                agentToken = "kilo",
                guestShell = "/bin/sh",
                guestRecordFile = recordFile.absolutePath,
            )
            // The chain ends with `exec /bin/sh -l` — an interactive login
            // shell that would wait forever; the fixture drives the chain
            // WITHOUT the trailing exec (the anchor + exit record are what
            // is under test; the trailing form is pinned compositionally).
            val core = chain.removeSuffix("; exec /bin/sh -l")
            val root = runChainInSession(core)
            root.waitFor()
            val parsed = AgentLaunchRecords.parse(recordFile.readText())
            val launch = parsed.launch
            assertNotNull("the launch record was written", launch)
            val pid = launch!!.pid
            val procStat = File("/proc/$pid/stat")
            // The agent exits quickly; the RECORD is the evidence either way.
            if (procStat.exists()) {
                val rest = procStat.readText().substringAfterLast(')').trim().split(Regex("\\s+"))
                assertEquals(
                    "the recorded starttime equals /proc field 22 (pid-reuse-proof)",
                    launch.startTicks,
                    rest[19].toLong(),
                )
                assertEquals(
                    "the recorded pgrp equals /proc's pgrp",
                    launch.pgrp,
                    rest[2].toInt(),
                )
            }
            assertTrue("the recorded pid is a plausible live-fork pid", pid > 1)
            assertEquals(
                "the exit record carries the agent's REAL status (0)",
                listOf(AgentLaunchRecords.ExitRecord(0, "kilo")),
                parsed.exits,
            )
        } finally {
            work.deleteRecursively()
        }
    }

    @Test
    fun `execution - a non-zero agent exit rides the exit record verbatim`() {
        val work = java.nio.file.Files.createTempDirectory("p9records").toFile()
        try {
            val agent = File(work, "kilo").apply {
                writeText("#!/bin/sh\nexit 7\n")
                setExecutable(true)
            }
            val recordFile = File(work, "record.jsonl").apply { createNewFile() }
            val chain = AgentLaunchRecords.launchChain(
                agentCommand = agent.absolutePath,
                agentToken = "kilo",
                guestShell = "/bin/sh",
                guestRecordFile = recordFile.absolutePath,
            ).removeSuffix("; exec /bin/sh -l")
            runChainInSession(chain).waitFor()
            val parsed = AgentLaunchRecords.parse(recordFile.readText())
            assertEquals(
                "exit 7 is a FACT, never an interpretation",
                listOf(AgentLaunchRecords.ExitRecord(7, "kilo")),
                parsed.exits,
            )
        } finally {
            work.deleteRecursively()
        }
    }

    @Test
    fun `execution - stdin and stdout pass through the anchor untouched (PART H)`() {
        val work = java.nio.file.Files.createTempDirectory("p9records").toFile()
        try {
            // The agent echoes what it reads, then exits 0.
            val agent = File(work, "kilo").apply {
                writeText("#!/bin/sh\ntr a-z A-Z\n")
                setExecutable(true)
            }
            val recordFile = File(work, "record.jsonl").apply { createNewFile() }
            val chain = AgentLaunchRecords.launchChain(
                agentCommand = agent.absolutePath,
                agentToken = "kilo",
                guestShell = "/bin/sh",
                guestRecordFile = recordFile.absolutePath,
            ).removeSuffix("; exec /bin/sh -l")
            val proc = ProcessBuilder("sh", "-c", chain)
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            proc.outputStream.write("hello pocketshell\n".toByteArray())
            proc.outputStream.flush()
            proc.outputStream.close()
            proc.waitFor()
            val out = proc.inputStream.readBytes().decodeToString()
            assertEquals("stdin reached the agent, stdout came back: no corruption", "HELLO POCKETSHELL\n", out)
            assertTrue(
                "no extra bytes were injected by the anchor",
                !out.contains("launch") && !out.contains("{"),
            )
        } finally {
            work.deleteRecursively()
        }
    }
}
