package app.pocketshell.runtime

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertThrows
import org.junit.rules.TemporaryFolder

/**
 * M2.3: pins the proot launch contract that was rehearsed end-to-end in the
 * sandbox (scripts/rehearse_m23_gate.sh, gate `uname; id; echo hello` passed).
 * Any change to argv/env semantics must consciously update these pins.
 */
class RuntimeProcessLauncherTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun makeNativeDir(withLoader32: Boolean = false): File {
        val dir = tmp.newFolder("native-${System.nanoTime()}")
        File(dir, RuntimeProcessLauncher.PROOT_LIB).writeText("proot")
        File(dir, RuntimeProcessLauncher.TALLOC_LIB).writeText("talloc")
        File(dir, RuntimeProcessLauncher.LOADER_LIB).writeText("loader")
        if (withLoader32) File(dir, RuntimeProcessLauncher.LOADER32_LIB).writeText("loader32")
        return dir
    }

    private fun spec(rootfs: File, nativeDir: File): RuntimeProcessLauncher.LaunchSpec =
        RuntimeProcessLauncher.buildLaunchSpec(
            nativeLibraryDir = nativeDir.absolutePath,
            rootfsDir = rootfs,
            hostCwd = tmp.newFolder("cwd-${System.nanoTime()}"),
            prootTmpDir = tmp.newFolder("proot-tmp-${System.nanoTime()}"),
        )

    @Test
    fun `argv matches the rehearsed proot contract`() {
        val rootfs = tmp.newFolder("rootfs")
        val native = makeNativeDir()
        val s = spec(rootfs, native)
        assertEquals(
            listOf(
                File(native, RuntimeProcessLauncher.PROOT_LIB).absolutePath, // argv[0]
                "--kill-on-exit",
                "--rootfs=${rootfs.absolutePath}",
                "--root-id",
                "--cwd=/root",
                "--bind=/dev",
                "--bind=/proc",
                "--bind=/sys",
                "/bin/sh",
                "-l",
            ),
            s.arguments,
        )
    }

    /**
     * v0.3.2 regression pin (Samsung SM-F711B recording): bionic quotes argv[0]
     * in link/exec errors and proot's getopt starts at argv[1]. v0.3.1 shipped
     * argv[0]="--kill-on-exit", so the device reported `CANNOT LINK EXECUTABLE
     * "--kill-on-exit"` and the flag itself was silently swallowed as the
     * program-name slot. argv[0] must be the executable, always.
     */
    @Test
    fun `argv0 is the executable path`() {
        val s = spec(tmp.newFolder("rootfs"), makeNativeDir())
        assertEquals(s.executable, s.arguments.first())
        assertEquals("--kill-on-exit", s.arguments[1])
    }

    @Test
    fun `long options use the joined equals form`() {
        // proot v5.1.107.92 rejects the separated form ("--rootfs value") —
        // rehearsed in the sandbox: "option '--rootfs' and its value must be
        // separated by '='". Flag-like options are the only exceptions.
        val s = spec(tmp.newFolder("rootfs"), makeNativeDir())
        val flags = setOf("--kill-on-exit", "--root-id")
        for (arg in s.arguments) {
            if (arg.startsWith("--")) {
                assertTrue("bare long option leaked: $arg", arg.contains('=') || arg in flags)
            }
        }
        assertTrue(s.arguments.none { it.startsWith("--rootfs ") })
        assertTrue(s.arguments.any { it.startsWith("--rootfs=") })
    }

    @Test
    fun `guest shell is the final command element`() {
        val s = spec(tmp.newFolder("rootfs"), makeNativeDir())
        val i = s.arguments.indexOf(RuntimeProcessLauncher.GUEST_SHELL)
        assertEquals(s.arguments.size - 2, i)
        assertEquals("-l", s.arguments.last())
        assertTrue(s.arguments.indexOf("/bin/sh") > s.arguments.indexOf("--bind=/sys"))
    }

    @Test
    fun `environment wires loader paths and guest environment`() {
        val native = makeNativeDir()
        val s = spec(tmp.newFolder("rootfs"), native)
        val map = s.environment.associate {
            val i = it.indexOf('=')
            it.substring(0, i) to it.substring(i + 1)
        }
        assertEquals(
            File(native, RuntimeProcessLauncher.LOADER_LIB).absolutePath,
            map["PROOT_LOADER"],
        )
        assertEquals(native.absolutePath, map["LD_LIBRARY_PATH"])
        assertTrue((map["PROOT_TMP_DIR"] ?: "").isNotEmpty())
        assertEquals("/root", map["HOME"])
        assertEquals("/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin", map["PATH"])
        assertTrue((map["TERM"] ?: "").isNotEmpty())
        assertFalse(map.containsKey("PROOT_LOADER_32"))
    }

    @Test
    fun `loader32 env is added only when the file exists`() {
        val s = spec(tmp.newFolder("rootfs"), makeNativeDir(withLoader32 = true))
        val l32 = s.environment.first { it.startsWith("PROOT_LOADER_32=") }
        assertTrue(l32.endsWith(RuntimeProcessLauncher.LOADER32_LIB))
    }

    /**
     * v0.3.2 regression pin: the exact device failure from the 2026-09-01
     * recording — `CANNOT LINK EXECUTABLE "--kill-on-exit": library
     * "libtalloc.so" not found: needed by main executable`. bionic resolves
     * DT_NEEDED only from its default paths + LD_LIBRARY_PATH; it never
     * searches the app's nativeLibraryDir on its own. The exec environment
     * must therefore carry LD_LIBRARY_PATH pointing at nativeLibraryDir (the
     * sandbox rehearsal masked this by exporting it in the shell).
     */
    @Test
    fun `environment carries LD_LIBRARY_PATH into the guest linker`() {
        val native = makeNativeDir()
        val s = spec(tmp.newFolder("rootfs"), native)
        val entry = s.environment.firstOrNull { it.startsWith("LD_LIBRARY_PATH=") }
        assertEquals("LD_LIBRARY_PATH=${native.absolutePath}", entry)
    }

    @Test
    fun `refuses missing rootfs, proot binary, or loader`() {
        val native = makeNativeDir()
        // missing rootfs
        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            RuntimeProcessLauncher.buildLaunchSpec(
                native.absolutePath,
                File(tmp.root, "no-such-rootfs"),
                tmp.root,
                tmp.root,
            )
        }
        // missing native binaries entirely
        val rootfs = tmp.newFolder("rootfs")
        val emptyNative = tmp.newFolder("empty-native")
        assertThrows(IllegalArgumentException::class.java) {
            RuntimeProcessLauncher.buildLaunchSpec(
                emptyNative.absolutePath,
                rootfs,
                tmp.root,
                tmp.root,
            )
        }
        // proot present but loader missing
        val onlyProot = tmp.newFolder("only-proot")
        File(onlyProot, RuntimeProcessLauncher.PROOT_LIB).writeText("x")
        assertThrows(IllegalArgumentException::class.java) {
            RuntimeProcessLauncher.buildLaunchSpec(
                onlyProot.absolutePath,
                rootfs,
                tmp.root,
                tmp.root,
            )
        }
    }

    /**
     * v0.3.1 regression pin: the preflight reports the EXACT v0.3.0 device
     * crash (extractNativeLibs=false -> empty nativeLibraryDir) as an honest
     * string instead of an escaping require(), and never throws itself.
     */
    @Test
    fun `preflight describes an empty nativeLibraryDir instead of crashing`() {
        val rootfs = tmp.newFolder("rootfs")
        val emptyNative = tmp.newFolder("empty-native")
        val problem = RuntimeProcessLauncher.preconditionProblem(emptyNative.absolutePath, rootfs)
        assertTrue(problem != null)
        assertTrue("message must name proot", problem!!.contains("proot"))
        assertTrue("message must name the dir", problem.contains(emptyNative.absolutePath))
        // ... and buildLaunchSpec throws the SAME message (consistency pin)
        val thrown = assertThrows(IllegalArgumentException::class.java) {
            RuntimeProcessLauncher.buildLaunchSpec(emptyNative.absolutePath, rootfs, tmp.root, tmp.root)
        }
        assertEquals(problem, thrown.message)
    }

    @Test
    fun `preflight reports missing rootfs with repair guidance`() {
        val native = makeNativeDir()
        val problem = RuntimeProcessLauncher.preconditionProblem(
            native.absolutePath,
            File(tmp.root, "no-such-rootfs"),
        )
        assertTrue(problem != null)
        assertTrue(problem!!.contains("Diagnostics"))
    }

    @Test
    fun `preflight is null when every precondition holds`() {
        val rootfs = tmp.newFolder("rootfs")
        assertEquals(null, RuntimeProcessLauncher.preconditionProblem(makeNativeDir().absolutePath, rootfs))
    }

    @Test
    fun `preflight reports a missing loader even when proot exists`() {
        val rootfs = tmp.newFolder("rootfs")
        val onlyProot = tmp.newFolder("only-proot")
        File(onlyProot, RuntimeProcessLauncher.PROOT_LIB).writeText("x")
        val problem = RuntimeProcessLauncher.preconditionProblem(onlyProot.absolutePath, rootfs)
        assertTrue(problem != null)
        assertTrue("message must name the loader", problem!!.contains("loader"))
    }

    /**
     * v0.3.2 regression pin: proot + loader present but libtalloc.so missing
     * is the file-level shape of the device linker failure — preflight must
     * describe it honestly instead of spawning a process that can only die.
     */
    @Test
    fun `preflight reports a missing libtalloc`() {
        val rootfs = tmp.newFolder("rootfs")
        val noTalloc = tmp.newFolder("no-talloc")
        File(noTalloc, RuntimeProcessLauncher.PROOT_LIB).writeText("x")
        File(noTalloc, RuntimeProcessLauncher.LOADER_LIB).writeText("x")
        val problem = RuntimeProcessLauncher.preconditionProblem(noTalloc.absolutePath, rootfs)
        assertTrue(problem != null)
        assertTrue("message must name libtalloc.so", problem!!.contains("libtalloc.so"))
        assertTrue("message must name the dir", problem.contains(noTalloc.absolutePath))
    }

    @Test
    fun `executable points into nativeLibraryDir`() {
        val native = makeNativeDir()
        val s = spec(tmp.newFolder("rootfs"), native)
        assertEquals(File(native, RuntimeProcessLauncher.PROOT_LIB).absolutePath, s.executable)
    }

    /**
     * M2.4: package commands reuse the SAME spec builder — only the guest
     * argv tail differs (apk command instead of /bin/sh -l). v0.4.2: package
     * commands also pass bindProc=false (SELinux hardlink neverallow — see
     * buildLaunchSpec KDoc), so the pinned package-op argv has NO /proc bind.
     */
    @Test
    fun `guestCommand replaces the shell as the proot argv tail`() {
        val rootfs = tmp.newFolder("rootfs")
        val native = makeNativeDir()
        val s = RuntimeProcessLauncher.buildLaunchSpec(
            nativeLibraryDir = native.absolutePath,
            rootfsDir = rootfs,
            hostCwd = tmp.root,
            prootTmpDir = tmp.root,
            guestCommand = listOf("/sbin/apk", "add", "nano"),
            bindProc = false,
        )
        assertEquals(
            listOf(
                File(native, RuntimeProcessLauncher.PROOT_LIB).absolutePath,
                "--kill-on-exit",
                "--rootfs=${rootfs.absolutePath}",
                "--root-id",
                "--cwd=/root",
                "--bind=/dev",
                "--bind=/sys",
                "/sbin/apk",
                "add",
                "nano",
            ),
            s.arguments,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty guestCommand is refused`() {
        val rootfs = tmp.newFolder("rootfs")
        RuntimeProcessLauncher.buildLaunchSpec(
            makeNativeDir().absolutePath,
            rootfs,
            tmp.root,
            tmp.root,
            guestCommand = emptyList(),
        )
    }

    @Test
    fun `gate is READY-only across every state`() {
        for (state in RuntimeState.entries) {
            assertEquals(
                "state $state must ${if (state == RuntimeState.READY) "" else "not "}be enterable",
                state == RuntimeState.READY,
                RuntimeProcessLauncher.canEnterLinuxShell(state),
            )
        }
    }

    /**
     * v0.4.1: the apk cache binds must sit AFTER the fixed binds and BEFORE
     * the guest argv — same proot --bind=host:guest mechanism, app-owned host
     * dirs, both apk-tools 3 cache locations covered.
     * v0.4.2: package specs pass bindProc=false, so the full pinned package
     * argv has /dev + /sys + cache binds and NEVER /proc.
     */
    @Test
    fun `apkCacheDir adds cache binds before the guest argv`() {
        val rootfs = tmp.newFolder("rootfs")
        val native = makeNativeDir()
        val cache = tmp.newFolder("apk-cache")
        val s = RuntimeProcessLauncher.buildLaunchSpec(
            nativeLibraryDir = native.absolutePath,
            rootfsDir = rootfs,
            hostCwd = tmp.root,
            prootTmpDir = tmp.root,
            guestCommand = listOf("/sbin/apk", "update"),
            apkCacheDir = cache,
            bindProc = false,
        )
        assertEquals(
            listOf(
                File(native, RuntimeProcessLauncher.PROOT_LIB).absolutePath,
                "--kill-on-exit",
                "--rootfs=${rootfs.absolutePath}",
                "--root-id",
                "--cwd=/root",
                "--bind=/dev",
                "--bind=/sys",
                "--bind=${File(cache, "etc").absolutePath}:${RuntimeProcessLauncher.GUEST_APK_CACHE_ETC}",
                "--bind=${File(cache, "var").absolutePath}:${RuntimeProcessLauncher.GUEST_APK_CACHE_VAR}",
                "/sbin/apk",
                "update",
            ),
            s.arguments,
        )
        // bind targets must exist host-side (proot skips missing bindings)
        assertTrue(File(cache, "etc").isDirectory)
        assertTrue(File(cache, "var").isDirectory)
    }

    @Test
    fun `no apkCacheDir means no cache binds (shell path unchanged)`() {
        val rootfs = tmp.newFolder("rootfs")
        val s = spec(rootfs, makeNativeDir())
        assertTrue(s.arguments.none { it.startsWith("--bind=") && it.contains(":") && !it.startsWith("--bind=/dev") && !it.startsWith("--bind=/proc") && !it.startsWith("--bind=/sys") })
        assertEquals(listOf(RuntimeProcessLauncher.GUEST_SHELL, "-l"), s.arguments.takeLast(2))
    }

    /**
     * v0.4.2 ROOT-CAUSE PIN (Samsung SM-F711B 2026-09-02 screenshots):
     * package commands must NEVER carry a /proc bind. With /proc visible,
     * apk-tools 3.0.x commits downloads via
     * linkat("/proc/self/fd/N", …, AT_SYMLINK_FOLLOW), which AOSP
     * app_neverallows.te forbids for untrusted apps — the link fails with
     * EACCES and apk cancels the whole download ("updating and opening …:
     * Permission denied"). Without /proc, apk's is_proc_fd_ok() is false and
     * it commits via named-tmpfile + renameat (create/rename — allowed).
     * Interactive shell specs keep /proc.
     */
    @Test
    fun `package specs never bind proc while the shell keeps it`() {
        val rootfs = tmp.newFolder("rootfs")
        val native = makeNativeDir()
        val cache = tmp.newFolder("apk-cache")
        val packageSpec = RuntimeProcessLauncher.buildLaunchSpec(
            nativeLibraryDir = native.absolutePath,
            rootfsDir = rootfs,
            hostCwd = tmp.root,
            prootTmpDir = tmp.root,
            guestCommand = listOf("/sbin/apk", "add", "nano"),
            apkCacheDir = cache,
            bindProc = false,
        )
        assertTrue(packageSpec.arguments.none { it == "--bind=/proc" })
        assertTrue(packageSpec.arguments.any { it == "--bind=/dev" })
        assertTrue(packageSpec.arguments.any { it == "--bind=/sys" })

        val shellSpec = spec(rootfs, native)
        assertTrue("interactive sessions must keep /proc", shellSpec.arguments.any { it == "--bind=/proc" })
    }
}
