package app.pocketshell.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeStateTest {

    @Test
    fun `legal forward pipeline transitions are accepted`() {
        assertTrue(RuntimeState.NOT_INSTALLED.canTransitionTo(RuntimeState.DOWNLOADING))
        assertTrue(RuntimeState.DOWNLOADING.canTransitionTo(RuntimeState.VERIFYING))
        assertTrue(RuntimeState.VERIFYING.canTransitionTo(RuntimeState.EXTRACTING))
        assertTrue(RuntimeState.EXTRACTING.canTransitionTo(RuntimeState.CONFIGURING))
        assertTrue(RuntimeState.CONFIGURING.canTransitionTo(RuntimeState.READY))
    }

    @Test
    fun `failure exits exist from every pipeline stage`() {
        assertTrue(RuntimeState.DOWNLOADING.canTransitionTo(RuntimeState.FAILED))
        assertTrue(RuntimeState.VERIFYING.canTransitionTo(RuntimeState.FAILED))
        assertTrue(RuntimeState.EXTRACTING.canTransitionTo(RuntimeState.FAILED))
        assertTrue(RuntimeState.CONFIGURING.canTransitionTo(RuntimeState.FAILED))
        assertTrue(RuntimeState.CONFIGURING.canTransitionTo(RuntimeState.REPAIR_REQUIRED))
        assertTrue(RuntimeState.READY.canTransitionTo(RuntimeState.REPAIR_REQUIRED))
    }

    @Test
    fun `retry paths exist from FAILED and REPAIR_REQUIRED`() {
        assertTrue(RuntimeState.FAILED.canTransitionTo(RuntimeState.DOWNLOADING))
        assertTrue(RuntimeState.REPAIR_REQUIRED.canTransitionTo(RuntimeState.DOWNLOADING))
        assertTrue(RuntimeState.FAILED.canTransitionTo(RuntimeState.NOT_INSTALLED))
        assertTrue(RuntimeState.REPAIR_REQUIRED.canTransitionTo(RuntimeState.NOT_INSTALLED))
    }

    @Test
    fun `removal is possible from terminal states`() {
        assertTrue(RuntimeState.READY.canTransitionTo(RuntimeState.NOT_INSTALLED))
    }

    @Test
    fun `stages cannot be skipped`() {
        assertFalse(RuntimeState.NOT_INSTALLED.canTransitionTo(RuntimeState.READY))
        assertFalse(RuntimeState.NOT_INSTALLED.canTransitionTo(RuntimeState.EXTRACTING))
        assertFalse(RuntimeState.DOWNLOADING.canTransitionTo(RuntimeState.CONFIGURING))
        assertFalse(RuntimeState.DOWNLOADING.canTransitionTo(RuntimeState.EXTRACTING))
        assertFalse(RuntimeState.VERIFYING.canTransitionTo(RuntimeState.CONFIGURING))
        assertFalse(RuntimeState.EXTRACTING.canTransitionTo(RuntimeState.READY))
    }

    @Test
    fun `in-flight states reject starting over`() {
        assertFalse(RuntimeState.DOWNLOADING.canTransitionTo(RuntimeState.DOWNLOADING))
        assertFalse(RuntimeState.READY.canTransitionTo(RuntimeState.DOWNLOADING))
    }

    @Test
    fun `install may only start from honest restart states`() {
        assertTrue(RuntimeState.Companion.canStartInstall(RuntimeState.NOT_INSTALLED))
        assertTrue(RuntimeState.Companion.canStartInstall(RuntimeState.FAILED))
        assertTrue(RuntimeState.Companion.canStartInstall(RuntimeState.REPAIR_REQUIRED))
        assertFalse(RuntimeState.Companion.canStartInstall(RuntimeState.READY))
        assertFalse(RuntimeState.Companion.canStartInstall(RuntimeState.DOWNLOADING))
        assertFalse(RuntimeState.Companion.canStartInstall(RuntimeState.UNSUPPORTED_ABI))
    }

    @Test
    fun `expectsRuntimeOnDisk tracks disk reality`() {
        assertTrue(RuntimeState.READY.expectsRuntimeOnDisk)
        assertTrue(RuntimeState.REPAIR_REQUIRED.expectsRuntimeOnDisk)
        assertFalse(RuntimeState.NOT_INSTALLED.expectsRuntimeOnDisk)
        assertFalse(RuntimeState.DOWNLOADING.expectsRuntimeOnDisk)
    }

    @Test
    fun `unsupported abi can only go back to not installed`() {
        assertTrue(RuntimeState.UNSUPPORTED_ABI.canTransitionTo(RuntimeState.NOT_INSTALLED))
        assertEquals(1, RuntimeState.UNSUPPORTED_ABI.let { state ->
            RuntimeState.entries.count { state.canTransitionTo(it) }
        })
    }
}
