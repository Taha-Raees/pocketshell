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
        val s = spec(rootfs, makeNativeDir())
        assertEquals(
            listOf(
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

    @Test
    fun `executable points into nativeLibraryDir`() {
        val native = makeNativeDir()
        val s = spec(tmp.newFolder("rootfs"), native)
        assertEquals(File(native, RuntimeProcessLauncher.PROOT_LIB).absolutePath, s.executable)
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
}
