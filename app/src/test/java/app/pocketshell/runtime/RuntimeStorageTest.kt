package app.pocketshell.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RuntimeStorageTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newStorage(): RuntimeStorage = RuntimeStorage(tmp.newFolder("base"))

    private fun fakeRuntime(storage: RuntimeStorage, withMetadata: Boolean) {
        File(storage.rootfsDir, "etc").mkdirs()
        File(storage.rootfsDir, "etc/alpine-release").writeText("3.24.1")
        File(storage.rootfsDir, "bin").mkdirs()
        File(storage.rootfsDir, "bin/busybox").writeText("ELF...")
        if (withMetadata) {
            RuntimeMetadata.write(
                storage.metadataFile,
                RuntimeMetadata(
                    distribution = "Alpine",
                    distributionVersion = "3.24.1",
                    architecture = "aarch64",
                    installedAtEpochMs = 1L,
                    rootfsSha256 = "f55a90f6",
                    state = "READY",
                ),
            )
        }
    }

    @Test
    fun `layout matches architecture doc`() {
        val storage = newStorage()
        assertEquals("runtime", storage.rootDir.name)
        assertEquals("rootfs", storage.rootfsDir.name)
        assertEquals("runtime.json", storage.metadataFile.name)
        assertEquals("runtime-download.tmp", storage.downloadTmp.name)
        assertEquals("runtime-extract.tmp", storage.extractTmp.name)
        assertTrue(storage.downloadTmp.parentFile == storage.rootDir.parentFile)
    }

    @Test
    fun `reconcile on clean base - not installed and tmp cleaned`() {
        val storage = newStorage()
        storage.downloadTmp.writeText("partial garbage")
        assertEquals(RuntimeState.NOT_INSTALLED, storage.reconcileInitialState())
        assertFalse(storage.downloadTmp.exists())
        assertFalse(storage.extractTmp.exists())
    }

    @Test
    fun `reconcile with valid runtime and metadata - ready`() {
        val storage = newStorage()
        fakeRuntime(storage, withMetadata = true)
        assertEquals(RuntimeState.READY, storage.reconcileInitialState())
    }

    @Test
    fun `reconcile with runtime but broken metadata - repair required and data kept`() {
        val storage = newStorage()
        fakeRuntime(storage, withMetadata = false)
        storage.metadataFile.writeText("!!! not json !!!")
        assertEquals(RuntimeState.REPAIR_REQUIRED, storage.reconcileInitialState())
        assertTrue("user data must not be silently deleted", storage.runtimeDirExists())
    }

    @Test
    fun `reconcile with runtime missing busybox - repair required`() {
        val storage = newStorage()
        fakeRuntime(storage, withMetadata = true)
        File(storage.rootfsDir, "bin/busybox").delete()
        assertEquals(RuntimeState.REPAIR_REQUIRED, storage.reconcileInitialState())
    }

    @Test
    fun `promotion requires staging and empty destination`() {
        val storage = newStorage()
        // no staging -> fails
        try {
            storage.promoteStagedToRuntime()
            throw AssertionError("expected failure without staging")
        } catch (expected: IllegalStateException) {
            // ok
        }
        // staging + occupied destination -> fails, nothing destroyed
        File(storage.extractTmp, RuntimeStorage.DIR_ROOTFS).mkdirs()
        storage.rootDir.mkdirs()
        val sentinel = File(storage.rootDir, "sentinel").apply { writeText("keep me") }
        try {
            storage.promoteStagedToRuntime()
            throw AssertionError("expected failure with occupied destination")
        } catch (expected: IllegalStateException) {
            // ok
        }
        assertNotNull(sentinel.readText())
    }

    @Test
    fun `promotion moves staging into place atomically-named`() {
        val storage = newStorage()
        File(storage.extractTmp, RuntimeStorage.DIR_ROOTFS).mkdirs()
        File(storage.extractTmp, RuntimeStorage.FILE_METADATA).writeText("{}")
        storage.promoteStagedToRuntime()
        assertTrue(storage.runtimeDirExists())
        assertTrue(storage.metadataFile.isFile)
        assertFalse(storage.extractTmp.exists())
    }

    @Test
    fun `clear runtime is idempotent`() {
        val storage = newStorage()
        fakeRuntime(storage, withMetadata = true)
        assertTrue(storage.clearRuntime())
        assertFalse(storage.runtimeDirExists())
        assertTrue(storage.clearRuntime())
    }
}
