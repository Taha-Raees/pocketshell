package app.pocketshell.packages

import app.pocketshell.packages.PackageOperationState.FAILED
import app.pocketshell.packages.PackageOperationState.INSTALLING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.4.2 honesty pins for the catalog cards: "Working…" may appear ONLY on
 * the card the running operation targets. v0.4.1 showed it on every card
 * (global busy flag), which the user's 2026-09-02 screenshot captured — five
 * "Working…" buttons while only nano was being installed.
 */
class PackageModelsTest {

    private fun op(
        packageName: String? = "nano",
        state: PackageOperationState = INSTALLING,
    ) = PackageOperation(
        id = 1L,
        kind = PackageOperationKind.INSTALL,
        packageName = packageName,
        state = state,
        startTimeEpochMs = 0L,
    )

    @Test
    fun `busy targets only the card whose package the running operation installs`() {
        assertTrue(packageOperationTargetsCard(packageBusy = true, operation = op(packageName = "nano"), apkPackageName = "nano"))
        assertFalse("other cards must keep their true state", packageOperationTargetsCard(packageBusy = true, operation = op(packageName = "nano"), apkPackageName = "htop"))
    }

    @Test
    fun `no busy flag means no card claims work`() {
        assertFalse(packageOperationTargetsCard(packageBusy = false, operation = op(), apkPackageName = "nano"))
    }

    @Test
    fun `finished operations never claim card work`() {
        assertFalse(packageOperationTargetsCard(packageBusy = true, operation = op(state = FAILED), apkPackageName = "nano"))
    }

    @Test
    fun `repository updates target no card at all`() {
        assertFalse(packageOperationTargetsCard(packageBusy = true, operation = op(packageName = null), apkPackageName = "nano"))
        assertFalse(packageOperationTargetsCard(packageBusy = true, operation = null, apkPackageName = "nano"))
    }

    @Test
    fun `package name is matched exactly - no prefix collisions`() {
        // e.g. "vim" must not light up a hypothetical "vimdiff" card via equals
        assertFalse(packageOperationTargetsCard(packageBusy = true, operation = op(packageName = "vim"), apkPackageName = "vimdiff"))
        assertEquals("nano", op().packageName)
    }
}
