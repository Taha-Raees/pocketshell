package app.pocketshell.packages

import app.pocketshell.runtime.RuntimeProcessLauncher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The REAL process executor against the host JVM's /bin/sh: pins stream
 * separation, exit codes, timeout destruction, and cancellation destroy —
 * the exact mechanics the device run relies on with proot.
 */
class GuestCommandRunnerTest {

    private val runner = ProcessBuilderGuestCommandRunner()

    private fun specOf(script: String): RuntimeProcessLauncher.LaunchSpec =
        RuntimeProcessLauncher.LaunchSpec(
            executable = "/bin/sh",
            arguments = listOf("/bin/sh", "-c", script),
            environment = listOf("PATH=/usr/bin:/bin", "LANG=C"),
            workingDirectory = System.getProperty("java.io.tmpdir"),
            guestLabel = "test",
        )

    @Test
    fun `separates streams and reports the real exit code`() {
        val process = runner.start(specOf("echo out-line; echo err-line 1>&2; exit 3"))
        val result = process.waitFor(10_000)
        assertEquals(3, result.exitCode)
        assertTrue(result.stdout.contains("out-line"))
        assertTrue(result.stderr.contains("err-line"))
        assertEquals(null, result.error)
        assertTrue(!result.success)
    }

    @Test
    fun `success is exit zero`() {
        val process = runner.start(specOf("echo ok"))
        val result = process.waitFor(10_000)
        assertTrue(result.success)
        assertEquals("ok\n", result.stdout)
    }

    @Test
    fun `timeout destroys the process and reports honestly`() {
        val process = runner.start(specOf("sleep 30"))
        val result = process.waitFor(300)
        assertNull(result.exitCode)
        assertTrue(!result.success)
        assertTrue(result.error!!.contains("did not finish"))
        assertTrue(result.error!!.contains("terminated"))
    }

    @Test
    fun `destroy unblocks a waiting waitFor`() {
        val process = runner.start(specOf("sleep 30"))
        Thread {
            Thread.sleep(200)
            process.destroy()
        }.start()
        val start = System.nanoTime()
        val result = process.waitFor(null) // no timeout: destroy must unblock
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertTrue("destroy should unblock quickly, took ${elapsedMs}ms", elapsedMs < 10_000)
        assertTrue(result.exitCode != 0)
    }

    @Test
    fun `large output on both streams does not deadlock`() {
        val process = runner.start(specOf("for i in \$(seq 1 2000); do echo stdout-\$i; echo stderr-\$i 1>&2; done; exit 0"))
        val result = process.waitFor(30_000)
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("stdout-2000"))
        assertTrue(result.stderr.contains("stderr-2000"))
    }

    @Test
    fun `environment is passed exactly and cwd honored`() = runBlocking {
        val spec = RuntimeProcessLauncher.LaunchSpec(
            executable = "/bin/sh",
            arguments = listOf("/bin/sh", "-c", "echo \$MYVAR; pwd"),
            environment = listOf("PATH=/usr/bin:/bin", "MYVAR=hello-guest"),
            workingDirectory = "/tmp",
            guestLabel = "test",
        )
        val result = runner.start(spec).waitFor(10_000)
        assertTrue(result.stdout.contains("hello-guest"))
        assertTrue(result.stdout.contains("/tmp"))
    }
}
