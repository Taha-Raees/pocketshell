package app.pocketshell.packages

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the operation state machine: honest state progression mapped to real
 * work, single-flight refusal, and verification-gated SUCCESS (never claimed
 * without the guest confirming).
 */
class PackageOperationManagerTest {

    /** Scripted PackageManager whose behavior the tests control per-method. */
    private class FakePackages(
        var updateResult: PackageResult = PackageResult(success = true, exitCode = 0),
        var installResult: PackageResult = PackageResult(success = true, exitCode = 0),
        var uninstallResult: PackageResult = PackageResult(success = true, exitCode = 0),
        var infoResult: PackageInfoResult = PackageInfoResult(installed = true, version = "9.2-r0"),
        var executablePath: String? = "/usr/bin/nano",
        var delayInstallFirst: Boolean = false,
    ) : PackageManager {
        override suspend fun updateRepositories() = updateResult
        override suspend fun search(query: String): List<PackageSearchResult> = emptyList()
        override suspend fun getPackageInfo(packageName: String): PackageInfoResult = infoResult
        override suspend fun getInstalledVersions(packageNames: List<String>): Map<String, String> =
            emptyMap()
        override suspend fun install(packageName: String): PackageResult {
            if (delayInstallFirst) Thread.sleep(150) // window for the single-flight test
            return installResult
        }
        override suspend fun uninstall(packageName: String) = uninstallResult
        override suspend fun guestExecutablePath(executable: String) = executablePath
    }

    private val nano = CliAppCatalog.entries.first { it.id == "nano" }

    private fun manager(
        packages: FakePackages,
        ready: Boolean = true,
    ) = PackageOperationManager(
        runner = object : GuestCommandRunner {
            override fun start(spec: app.pocketshell.runtime.RuntimeProcessLauncher.LaunchSpec) =
                throw UnsupportedOperationException("fake packages never exec")
        },
        packagesFactory = { packages },
        runtimeReady = { ready },
        clock = { 0L },
    )

    private fun awaitTerminal(
        m: PackageOperationManager,
        kind: PackageOperationKind = PackageOperationKind.INSTALL,
        packageName: String? = "nano",
    ) {
        // operations run on the manager's IO scope; wait for THE op (matched
        // by kind+package, so an earlier refused op snapshot can't satisfy us)
        // to reach a terminal state
        val deadline = System.nanoTime() + 5_000_000_000
        while (System.nanoTime() < deadline) {
            val op = m.current.value
            if (op != null && op.kind == kind && op.packageName == packageName &&
                (op.state == PackageOperationState.SUCCESS || op.state == PackageOperationState.FAILED)
            ) return
            Thread.sleep(10)
        }
        error("operation never reached a terminal state: ${m.current.value}")
    }

    @Test
    fun `install walks the honest state machine to SUCCESS`() = runBlocking(Dispatchers.IO) {
        val m = manager(FakePackages())
        assertTrue(m.install(nano))
        awaitTerminal(m)
        val op = m.current.value!!
        assertEquals(PackageOperationKind.INSTALL, op.kind)
        assertEquals("nano", op.packageName)
        assertEquals(PackageOperationState.SUCCESS, op.state)
        // SUCCESS carries the real executable path the guest reported
        assertEquals("/usr/bin/nano", op.stdoutTail)
    }

    @Test
    fun `failed repository update fails the install before apk add`() = runBlocking {
        val m = manager(
            FakePackages(
                updateResult = PackageResult(
                    success = false,
                    exitCode = 2,
                    stderr = "ERROR: unable to resolve 'dl-cdn.alpinelinux.org'",
                ),
            ),
        )
        assertTrue(m.install(nano))
        awaitTerminal(m)
        val op = m.current.value!!
        assertEquals(PackageOperationState.FAILED, op.state)
        assertTrue(op.error!!.contains("repository update failed"))
        assertTrue(op.stderrTail.contains("unable to resolve"))
    }

    @Test
    fun `SUCCESS is refused when the guest cannot confirm the package`() = runBlocking {
        val m = manager(FakePackages(infoResult = PackageInfoResult(installed = false)))
        assertTrue(m.install(nano))
        awaitTerminal(m)
        val op = m.current.value!!
        assertEquals(PackageOperationState.FAILED, op.state)
        assertTrue(op.error!!.contains("refusing to claim installation"))
    }

    @Test
    fun `SUCCESS is refused when the executable cannot be verified`() = runBlocking {
        val m = manager(FakePackages(executablePath = null))
        assertTrue(m.install(nano))
        awaitTerminal(m)
        assertEquals(PackageOperationState.FAILED, m.current.value!!.state)
        assertTrue(m.current.value!!.error!!.contains("command -v"))
    }

    @Test
    fun `uninstall verifies real absence before SUCCESS`() = runBlocking {
        val m = manager(FakePackages(infoResult = PackageInfoResult(installed = false)))
        assertTrue(m.uninstall(nano))
        awaitTerminal(m, PackageOperationKind.UNINSTALL)
        assertEquals(PackageOperationState.SUCCESS, m.current.value!!.state)

        // package still there after apk del = refuse
        val lying = manager(FakePackages(infoResult = PackageInfoResult(installed = true)))
        assertTrue(lying.uninstall(nano))
        awaitTerminal(lying, PackageOperationKind.UNINSTALL)
        assertEquals(PackageOperationState.FAILED, lying.current.value!!.state)
    }

    @Test
    fun `second mutating operation is refused while one runs`() = runBlocking {
        val m = manager(FakePackages(delayInstallFirst = true))
        assertTrue(m.install(nano))
        // The refusal snapshot may race the install's first state update —
        // poll until busy is observable, then try the second operation.
        val deadline = System.nanoTime() + 2_000_000_000
        while (!m.busy.value && System.nanoTime() < deadline) Thread.sleep(5)
        assertFalse(m.updateRepositories())
        val refusal = m.current.value
        assertEquals(PackageOperationKind.UPDATE_REPOSITORIES, refusal?.kind)
        assertEquals(PackageOperationState.FAILED, refusal?.state)
        assertTrue(refusal!!.error!!.contains("another package operation is already running"))
        awaitTerminal(m) // the INSTALL must still complete despite the refusal
        assertEquals(PackageOperationState.SUCCESS, m.current.value!!.state)
        assertFalse(m.busy.value)
    }

    @Test
    fun `refusal when runtime is not READY carries the honest reason`() = runBlocking {
        val m = manager(FakePackages(), ready = false)
        assertFalse(m.install(nano))
        assertEquals(PackageOperationState.FAILED, m.current.value!!.state)
        assertTrue(m.current.value!!.error!!.contains("not READY"))
    }

    @Test
    fun `cancel destroys the process and lands FAILED`() = runBlocking {
        var destroyed = false
        val m = PackageOperationManager(
            runner = object : GuestCommandRunner {
                override fun start(spec: app.pocketshell.runtime.RuntimeProcessLauncher.LaunchSpec) =
                    object : GuestProcess {
                        override fun waitFor(timeoutMs: Long?): ExecResult {
                            // simulate the real behaviour: destroy() unblocks waitFor
                            while (!destroyed) Thread.sleep(5)
                            return ExecResult(exitCode = 137, stdout = "", stderr = "")
                        }
                        override fun destroy() {
                            destroyed = true
                        }
                    }
            },
            packagesFactory = { runner ->
                // a REAL manager against this runner exercises the wrapped path
                AlpinePackageManager(
                    rootfsDir = java.nio.file.Files.createTempDirectory("rootfs").toFile(),
                    specFactory = { guestCommand ->
                        app.pocketshell.runtime.RuntimeProcessLauncher.buildLaunchSpec(
                            "/native",
                            java.nio.file.Files.createTempDirectory("rootfs2").toFile(),
                            java.nio.file.Files.createTempDirectory("cwd").toFile(),
                            java.nio.file.Files.createTempDirectory("ptmp").toFile(),
                            guestCommand = guestCommand,
                        )
                    },
                    runner = runner,
                    readyGuard = { null },
                )
            },
            runtimeReady = { true },
        )
        // update flows through the real AlpinePackageManager into the fake process
        m.updateRepositories()
        m.cancelCurrent()
        awaitTerminal(m, PackageOperationKind.UPDATE_REPOSITORIES, packageName = null)
        val op = m.current.value!!
        assertEquals(PackageOperationState.FAILED, op.state)
        assertTrue("destroyed process must surface as failed", op.exitCode == 137 || op.error != null)
        assertFalse(m.busy.value)
    }
}
