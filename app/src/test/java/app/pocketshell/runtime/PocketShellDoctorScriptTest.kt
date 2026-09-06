package app.pocketshell.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * m6.0.3 DOCTOR-CORRECTNESS GATE — regression tests for the SHIPPED script
 * (scripts/runtime/pocketshell-doctor → usr/local/bin inside the glibc layer).
 *
 * Device evidence that motivated this file: pocketshell-doctor v1 verdicted
 * UNSUPPORTED for EVERY versioned glibc binary — real cline 3.0.61 needs only
 * GLIBC_2.17, the layer provides 2.41, and the doctor still said UNSUPPORTED
 * (its "comparison" was structurally always-false). The device suite missed
 * it because the runner grepped "SUPPORTED" unanchored. The script's own
 * --selftest (15 cases: the exact semantic-comparison matrix + numeric max
 * extraction) is now executed here on every JVM run so the comparison logic
 * can never silently regress again.
 */
class PocketShellDoctorScriptTest {

    private val script: File = listOf(
        File("../scripts/runtime/pocketshell-doctor"),
        File("scripts/runtime/pocketshell-doctor"),
    ).firstOrNull { it.isFile } ?: error("pocketshell-doctor script not found in the repo tree")

    private fun run(vararg args: String, cwd: File? = null): Pair<Int, String> {
        val p = ProcessBuilder("sh", script.absolutePath, *args)
            .redirectErrorStream(true)
        if (cwd != null) p.directory(cwd)
        p.start().apply {
            val out = inputStream.bufferedReader().readText()
            assertTrue("doctor did not finish in time", waitFor(60, TimeUnit.SECONDS))
            return exitValue() to out
        }
    }

    // ------------------------------------------------------------ selftest

    @Test
    fun `doctor selftest - the semantic GLIBC comparison matrix - passes`() {
        val (rc, out) = run("--selftest")
        assertEquals("selftest must exit 0:\n$out", 0, rc)
        assertTrue(
            "selftest must print its summary:\n$out",
            out.contains("SELFTEST PASS (15/15)"),
        )
        // The exact cases from the device-gate report (Phase 7):
        assertTrue(out.contains("2.17 vs 2.41"))
        assertTrue(out.contains("2.42 vs 2.41"))
        assertTrue(out.contains("2.9"))
        assertTrue(out.contains("2.10"))
        // Numeric max extraction incl. the lexical trap:
        assertTrue(out.contains("max(GLIBC_2.9 GLIBC_2.10) = 2.10"))
    }

    // -------------------------------------------------------- usage contract

    @Test
    fun `doctor usage errors exit 2`() {
        val (rcNone, _) = run()
        assertEquals(2, rcNone)
        val (rcMissing, outMissing) = run("/nonexistent-binary-path")
        assertEquals("2 for not-a-file:\n$outMissing", 2, rcMissing)
    }

    // ------------------------------------- real-fixture fact extraction (aarch64)

    /**
     * The committed aarch64 fixture (runtime-tests/bin/t_cline_shape — the
     * Cline dependency-shape binary) is diagnosed with readelf-based fact
     * gathering. On the host there is no aarch64 loader, so the verdict is
     * UNSUPPORTED(layer-missing) — but the fact lines must be complete and
     * CORRECT: the maximum required GLIBC symbol version must be extracted
     * as GLIBC_2.34 (numeric max over {GLIBC_2.17, GLIBC_2.34}), exactly the
     * path that was broken in v1.
     */
    @Test
    fun `fixture diagnosis extracts the numeric max required GLIBC version`() {
        val readelfReady = ProcessBuilder("sh", "-c", "command -v readelf")
            .start().inputStream.bufferedReader().readText().isNotBlank()
        org.junit.Assume.assumeTrue("readelf not on this runner", readelfReady)

        val fixture = listOf(
            File("../runtime-tests/bin/t_cline_shape"),
            File("runtime-tests/bin/t_cline_shape"),
        ).firstOrNull { it.isFile }
        org.junit.Assume.assumeTrue("aarch64 fixture not on this runner", fixture != null)

        val (rc, out) = run(fixture!!.absolutePath)
        assertEquals("layer is absent on the host -> UNSUPPORTED:\n$out", 1, rc)
        assertTrue("Machine line:\n$out", out.contains("Machine: AArch64"))
        assertTrue("interpreter line:\n$out", out.contains("Interpreter: /lib/ld-linux-aarch64.so.1"))
        assertTrue("DT_NEEDED enumerated:\n$out", out.contains("libc.so.6"))
        assertTrue(
            "max required version must be the NUMERIC max of {2.17, 2.34}:\n$out",
            out.contains("Required GLIBC: GLIBC_2.34"),
        )
        assertTrue("version gate must be undecided (no layer on host):\n$out", out.contains("Version gate: UNDECIDED"))
        assertTrue(
            "verdict lines must stay anchored-grep compatible for the device suite:\n$out",
            out.contains("Compatibility: UNSUPPORTED"),
        )
    }

    @Test
    fun `static binary is classified without any loader`() {
        val fixture = listOf(
            File("../runtime-tests/bin/t_static"),
            File("runtime-tests/bin/t_static"),
        ).firstOrNull { it.isFile }
        org.junit.Assume.assumeTrue("static fixture not on this runner", fixture != null)
        val (rc, out) = run(fixture!!.absolutePath)
        assertEquals("static binaries are supported everywhere:\n$out", 0, rc)
        assertTrue(out.contains("Compatibility: SUPPORTED (static"))
    }
}
