package app.pocketshell.packages

import app.pocketshell.runtime.RuntimeProcessLauncher
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Pins the real command layer of [AlpinePackageManager] against a scripted
 * fake guest process runner: exact guest argv (single proot infrastructure —
 * the spec tail IS the apk command), exit-code semantics, DNS repair before
 * ops, and honest refusal when the runtime is not READY.
 */
class AlpinePackageManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val recordedSpecs = mutableListOf<RuntimeProcessLauncher.LaunchSpec>()

    /** A native dir that passes the real preflight (proot/loader/talloc). */
    private fun makeNativeDir(): String {
        val dir = tmp.newFolder("native-${System.nanoTime()}")
        java.io.File(dir, RuntimeProcessLauncher.PROOT_LIB).writeText("x")
        java.io.File(dir, RuntimeProcessLauncher.TALLOC_LIB).writeText("x")
        java.io.File(dir, RuntimeProcessLauncher.LOADER_LIB).writeText("x")
        return dir.absolutePath
    }

    private fun newRootfs(): File = tmp.newFolder("rootfs-${System.nanoTime()}")

    private fun fakeRunner(vararg results: ExecResult): GuestCommandRunner =
        object : GuestCommandRunner {
            private var i = 0
            override fun start(spec: RuntimeProcessLauncher.LaunchSpec): GuestProcess {
                recordedSpecs += spec
                val result = results[results.indices.elementAtOrElse(i) { results.lastIndex }]
                i++
                return object : GuestProcess {
                    override fun waitFor(timeoutMs: Long?) = result
                    override fun destroy() = Unit
                }
            }
        }

    private fun makeManager(
        runner: GuestCommandRunner,
        ready: Boolean = true,
    ): AlpinePackageManager {
        val rootfs = newRootfs()
        val native = makeNativeDir()
        return AlpinePackageManager(
            rootfsDir = rootfs,
            specFactory = { guestCommand ->
                RuntimeProcessLauncher.buildLaunchSpec(
                    nativeLibraryDir = native,
                    rootfsDir = rootfs,
                    hostCwd = rootfs,
                    prootTmpDir = rootfs,
                    guestCommand = guestCommand,
                )
            },
            runner = runner,
            readyGuard = { if (ready) null else "the Linux runtime is not READY" },
        )
    }

    private fun specToNativeLibDir(spec: RuntimeProcessLauncher.LaunchSpec): String =
        spec.environment.first { it.startsWith("LD_LIBRARY_PATH=") }.removePrefix("LD_LIBRARY_PATH=")

    @Test
    fun `update runs real apk update as the proot guest command`() = runBlocking {
        val rootfs = newRootfs()
        val native = makeNativeDir()
        File(rootfs, "etc").mkdirs()
        File(rootfs, "etc/resolv.conf").writeText("nameserver 1.1.1.1\n")
        val manager = AlpinePackageManager(
            rootfsDir = rootfs,
            specFactory = { guestCommand ->
                RuntimeProcessLauncher.buildLaunchSpec(
                    native, rootfs, rootfs, rootfs, guestCommand = guestCommand,
                )
            },
            runner = fakeRunner(ExecResult(exitCode = 0, stdout = "OK: 28645 distinct packages available\n", stderr = "")),
            readyGuard = { null },
        )
        val result = manager.updateRepositories()
        assertTrue(result.success)
        assertEquals(0, result.exitCode)
        val spec = recordedSpecs.single()
        // argv[0] = proot path (v0.3.2 contract), tail = the apk command
        assertEquals("$native/libproot.so", spec.arguments.first())
        assertEquals(listOf("/sbin/apk", "update"), spec.arguments.takeLast(2))
        assertEquals(native, specToNativeLibDir(spec))
    }

    @Test
    fun `install constructs apk add with the validated package name`() = runBlocking {
        val rootfs = newRootfs()
        val native = makeNativeDir()
        val manager = AlpinePackageManager(
            rootfsDir = rootfs,
            specFactory = { guestCommand ->
                RuntimeProcessLauncher.buildLaunchSpec(
                    native, rootfs, rootfs, rootfs, guestCommand = guestCommand,
                )
            },
            runner = fakeRunner(ExecResult(exitCode = 0, stdout = "", stderr = "")),
            readyGuard = { null },
        )
        assertTrue(manager.install("nano").success)
        assertEquals(listOf("/sbin/apk", "add", "nano"), recordedSpecs.single().arguments.takeLast(3))
    }

    @Test
    fun `invalid package name fails honestly without any exec`() = runBlocking {
        val manager = makeManager(fakeRunner(ExecResult(exitCode = 0, stdout = "", stderr = "")))
        val result = manager.install("bad name; rm")
        assertFalse(result.success)
        assertTrue(result.error!!.contains("invalid package name"))
        assertEquals(0, recordedSpecs.size)
    }

    @Test
    fun `getPackageInfo maps exit codes to installed state`() = runBlocking {
        val rootfs = newRootfs()
        val native = makeNativeDir()
        val manager = AlpinePackageManager(
            rootfsDir = rootfs,
            specFactory = { guestCommand ->
                RuntimeProcessLauncher.buildLaunchSpec(
                    native, rootfs, rootfs, rootfs, guestCommand = guestCommand,
                )
            },
            runner = fakeRunner(
                ExecResult(exitCode = 0, stdout = "nano-9.2-r0\n", stderr = ""),
                ExecResult(exitCode = 1, stdout = "", stderr = ""),
            ),
            readyGuard = { null },
        )
        val installed = manager.getPackageInfo("nano")
        assertTrue(installed.installed)
        assertEquals("9.2-r0", installed.version)
        assertEquals(listOf("/sbin/apk", "info", "-e", "-v", "nano"), recordedSpecs[0].arguments.takeLast(5))

        val gone = manager.getPackageInfo("nano")
        assertFalse(gone.installed)
    }

    @Test
    fun `search parses real output and empty query short-circuits`() = runBlocking {
        val rootfs = newRootfs()
        val native = makeNativeDir()
        val manager = AlpinePackageManager(
            rootfsDir = rootfs,
            specFactory = { guestCommand ->
                RuntimeProcessLauncher.buildLaunchSpec(
                    native, rootfs, rootfs, rootfs, guestCommand = guestCommand,
                )
            },
            runner = fakeRunner(
                ExecResult(exitCode = 0, stdout = "nano-9.2-r0\nnano-doc-9.2-r0\n", stderr = ""),
            ),
            readyGuard = { null },
        )
        val results = manager.search("nano")
        assertEquals(listOf("nano", "nano-doc"), results.map { it.name })
        assertEquals(listOf("/sbin/apk", "search", "nano"), recordedSpecs.single().arguments.takeLast(3))

        assertEquals(emptyList<PackageSearchResult>(), manager.search("  "))
    }

    @Test
    fun `guestExecutablePath uses POSIX command -v through a positional arg`() = runBlocking {
        val rootfs = newRootfs()
        val native = makeNativeDir()
        val manager = AlpinePackageManager(
            rootfsDir = rootfs,
            specFactory = { guestCommand ->
                RuntimeProcessLauncher.buildLaunchSpec(
                    native, rootfs, rootfs, rootfs, guestCommand = guestCommand,
                )
            },
            runner = fakeRunner(ExecResult(exitCode = 0, stdout = "/usr/bin/nano\n", stderr = "")),
            readyGuard = { null },
        )
        assertEquals("/usr/bin/nano", manager.guestExecutablePath("nano"))
        val argv = recordedSpecs.single().arguments
        assertEquals("/bin/sh", argv[argv.size - 5])
        assertEquals("-c", argv[argv.size - 4])
        assertEquals("sh", argv[argv.size - 2])
        assertEquals("nano", argv.last())
        assertNull(manager.guestExecutablePath("na no"))
    }

    @Test
    fun `dns repair runs before the first command and is not repeated needlessly`() = runBlocking {
        val rootfs = newRootfs()
        val native = makeNativeDir()
        val manager = AlpinePackageManager(
            rootfsDir = rootfs,
            specFactory = { guestCommand ->
                RuntimeProcessLauncher.buildLaunchSpec(
                    native, rootfs, rootfs, rootfs, guestCommand = guestCommand,
                )
            },
            runner = fakeRunner(
                ExecResult(exitCode = 0, stdout = "", stderr = ""),
                ExecResult(exitCode = 0, stdout = "", stderr = ""),
            ),
            readyGuard = { null },
        )
        // no resolv.conf yet — the first op must create it (marked, public pair)
        assertTrue(manager.uninstall("nano").success)
        val resolv = File(rootfs, "etc/resolv.conf")
        assertTrue(resolv.isFile)
        val text = resolv.readText()
        assertTrue(text.startsWith(app.pocketshell.runtime.GuestEnvironment.RESOLV_CONF_MARKER))
        assertTrue(text.contains("nameserver 1.1.1.1\n"))
        assertTrue(text.contains("nameserver 8.8.8.8\n"))
        // existing MANAGED content is refreshed to the current desired body
        resolv.writeText(app.pocketshell.runtime.GuestEnvironment.RESOLV_CONF_MARKER + "\nnameserver 9.9.9.9\n")
        assertTrue(manager.uninstall("nano").success)
        assertFalse(resolv.readText().contains("9.9.9.9"))
        // USER content (comments/options) is never clobbered
        resolv.writeText("# mine\nnameserver 9.9.9.9\noptions timeout:1\n")
        assertTrue(manager.uninstall("nano").success)
        assertEquals("# mine\nnameserver 9.9.9.9\noptions timeout:1\n", resolv.readText())
    }

    @Test
    fun `not-ready runtime refuses every operation with the real reason`() = runBlocking {
        val manager = makeManager(fakeRunner(), ready = false)
        val result = manager.updateRepositories()
        assertFalse(result.success)
        assertTrue(result.error!!.contains("not READY"))
        assertEquals(0, recordedSpecs.size)
        assertTrue(manager.search("nano").isEmpty())
        assertNull(manager.guestExecutablePath("nano"))
    }

    @Test
    fun `failed apk add carries the real stderr for honest errors`() = runBlocking {
        val rootfs = newRootfs()
        val native = makeNativeDir()
        File(rootfs, "etc").mkdirs()
        File(rootfs, "etc/resolv.conf").writeText("nameserver 1.1.1.1\n")
        val manager = AlpinePackageManager(
            rootfsDir = rootfs,
            specFactory = { guestCommand ->
                RuntimeProcessLauncher.buildLaunchSpec(
                    native, rootfs, rootfs, rootfs, guestCommand = guestCommand,
                )
            },
            runner = fakeRunner(
                ExecResult(
                    exitCode = 2,
                    stdout = "",
                    stderr = "ERROR: unable to select packages: nano (no such package)\n",
                ),
            ),
            readyGuard = { null },
        )
        val result = manager.install("nano")
        assertFalse(result.success)
        assertEquals(2, result.exitCode)
        // the REAL apk stderr is the honest error carrier (no synthetic blur)
        assertTrue(result.stderr.contains("no such package"))
        assertTrue(result.stderr.isNotBlank())
    }

    // ------------------------------------------------- v0.4.1 device lessons

    @Test
    fun `device dns servers flow into the pre-op repair and upgrade the v040 fallback`() = runBlocking {
        val rootfs = newRootfs()
        val native = makeNativeDir()
        val resolv = File(rootfs, "etc/resolv.conf")
        resolv.parentFile.mkdirs()
        // exactly what v0.4.0's repair wrote on the user's device
        resolv.writeText(app.pocketshell.runtime.GuestEnvironment.RESOLV_CONF_CONTENT)
        var providerCalls = 0
        val manager = AlpinePackageManager(
            rootfsDir = rootfs,
            specFactory = { guestCommand ->
                RuntimeProcessLauncher.buildLaunchSpec(
                    native, rootfs, rootfs, rootfs, guestCommand = guestCommand,
                )
            },
            runner = fakeRunner(ExecResult(exitCode = 0, stdout = "", stderr = "")),
            readyGuard = { null },
            dnsServers = { providerCalls++; listOf("192.168.1.1") },
        )
        assertTrue(manager.updateRepositories().success)
        val text = resolv.readText()
        assertTrue(text.startsWith(app.pocketshell.runtime.GuestEnvironment.RESOLV_CONF_MARKER))
        assertTrue(text.contains("nameserver 192.168.1.1\n"))
        assertTrue(text.contains("nameserver 8.8.8.8\n"))
        assertEquals(1, providerCalls)
    }

    @Test
    fun `apk workspace dirs are repaired before every operation`() = runBlocking {
        val rootfs = newRootfs()
        val native = makeNativeDir()
        val manager = AlpinePackageManager(
            rootfsDir = rootfs,
            specFactory = { guestCommand ->
                RuntimeProcessLauncher.buildLaunchSpec(
                    native, rootfs, rootfs, rootfs, guestCommand = guestCommand,
                )
            },
            runner = fakeRunner(ExecResult(exitCode = 0, stdout = "", stderr = "")),
            readyGuard = { null },
        )
        assertTrue(manager.search("nano").isEmpty()) // repair ran, exec returned no hits
        // uninstall runs the repair path again on the same rootfs
        recordedSpecs.clear()
        assertTrue(manager.uninstall("nano").success)
        for (relative in listOf(
            app.pocketshell.runtime.GuestEnvironment.APK_CACHE_ETC_RELATIVE,
            app.pocketshell.runtime.GuestEnvironment.APK_CACHE_VAR_RELATIVE,
            app.pocketshell.runtime.GuestEnvironment.APK_TMP_RELATIVE,
        )) {
            assertTrue(relative, File(rootfs, relative).isDirectory)
        }
    }

    @Test
    fun `workspace repair failure fails the operation honestly without exec`() = runBlocking {
        val rootfs = newRootfs()
        val native = makeNativeDir()
        // a FILE where the apk tmp DIRECTORY must be: repair cannot win
        val tmpPath = File(rootfs, app.pocketshell.runtime.GuestEnvironment.APK_TMP_RELATIVE)
        tmpPath.parentFile.mkdirs()
        tmpPath.writeText("blocker")
        val manager = AlpinePackageManager(
            rootfsDir = rootfs,
            specFactory = { guestCommand ->
                RuntimeProcessLauncher.buildLaunchSpec(
                    native, rootfs, rootfs, rootfs, guestCommand = guestCommand,
                )
            },
            runner = fakeRunner(),
            readyGuard = { null },
        )
        val result = manager.updateRepositories()
        assertFalse(result.success)
        assertTrue(result.error!!.contains("apk cache directories"))
        assertEquals(0, recordedSpecs.size)
    }
}
