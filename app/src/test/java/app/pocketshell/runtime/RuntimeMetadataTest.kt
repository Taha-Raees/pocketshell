package app.pocketshell.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RuntimeMetadataTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val sample = RuntimeMetadata(
        distribution = "Alpine",
        distributionVersion = "3.24.1",
        architecture = "aarch64",
        installedAtEpochMs = 1_700_000_000_000,
        rootfsSha256 = "f55a90f69052c5bd6f92cb09a8f47065970830b194c917a006fb94028e721259",
        state = "READY",
    )

    @Test
    fun `json round-trip preserves all fields`() {
        val file = File(tmp.root, "runtime.json")
        RuntimeMetadata.write(file, sample)
        assertEquals(sample, RuntimeMetadata.read(file))
        assertEquals(RuntimeMetadata.SCHEMA_VERSION, RuntimeMetadata.read(file)?.runtimeVersion)
    }

    @Test
    fun `unknown keys are tolerated forward-compat`() {
        val file = File(tmp.root, "future.json")
        file.writeText(
            """
            {
              "runtimeVersion": 1,
              "distribution": "Alpine",
              "distributionVersion": "3.24.1",
              "architecture": "aarch64",
              "installedAtEpochMs": 1700000000000,
              "rootfsSha256": "f55a90f6",
              "state": "READY",
              "someFutureField": {"nested": true}
            }
            """.trimIndent(),
        )
        val meta = RuntimeMetadata.read(file)
        assertEquals("Alpine", meta?.distribution)
        assertEquals("READY", meta?.state)
    }

    @Test
    fun `garbage file yields null instead of throwing`() {
        val file = File(tmp.root, "broken.json")
        file.writeText("{ this is not json !!!")
        assertNull(RuntimeMetadata.read(file))
    }

    @Test
    fun `missing file yields null`() {
        assertNull(RuntimeMetadata.read(File(tmp.root, "absent.json")))
    }

    @Test
    fun `write is atomic - no tmp file left behind`() {
        val file = File(tmp.root, "runtime.json")
        RuntimeMetadata.write(file, sample)
        assertEquals(emptyList<String>(), file.parentFile?.list()?.filter { it.endsWith(".write") })
    }
}
