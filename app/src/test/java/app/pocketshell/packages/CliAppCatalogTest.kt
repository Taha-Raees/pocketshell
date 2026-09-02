package app.pocketshell.packages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Catalog invariants (M2.4): metadata only, tiny, no shell-command "apps",
 * every entry launchable by real name via PATH (pinned by the rehearsal).
 */
class CliAppCatalogTest {

    @Test
    fun `catalog is deliberately small and unique`() {
        assertTrue("M2.4 keeps the catalog tiny", CliAppCatalog.entries.size <= 5)
        assertEquals(CliAppCatalog.entries.size, CliAppCatalog.entries.map { it.id }.toSet().size)
        assertEquals(CliAppCatalog.entries.size, CliAppCatalog.entries.map { it.apkPackageName }.toSet().size)
    }

    @Test
    fun `every entry is a meaningful CLI app - never a normal shell command`() {
        val forbidden = setOf(
            "sh", "ls", "cat", "pwd", "df", "ps", "ping", "top", "echo", "cd",
            "rm", "cp", "mv", "mkdir", "grep", "head", "tail", "which",
        )
        for (entry in CliAppCatalog.entries) {
            assertFalse(
                "${entry.executable} must never be a launcher card",
                entry.executable in forbidden,
            )
        }
    }

    @Test
    fun `every entry has complete honest metadata`() {
        for (entry in CliAppCatalog.entries) {
            assertTrue(entry.id.isNotBlank())
            assertTrue(entry.name.isNotBlank())
            assertTrue(entry.description.isNotBlank())
            assertEquals("catalog metadata must match the installed package", entry.id, entry.apkPackageName)
            assertTrue(entry.launchCommand.isNotEmpty())
            // launch command is the plain executable name — PATH-resolved in
            // the guest, proven by the rehearsal for nano
            assertEquals(entry.executable, entry.launchCommand.first())
        }
    }

    @Test
    fun `no installed field exists on catalog metadata`() {
        // reflection: the catalog class must not be able to lie about install state
        val fields = CliAppCatalogEntry::class.java.declaredFields.map { it.name }
        assertFalse("catalog must never carry an 'installed' field", "installed" in fields)
    }

    @Test
    fun `byId resolves entries`() {
        assertEquals("nano", CliAppCatalog.byId("nano")?.apkPackageName)
        assertEquals(null, CliAppCatalog.byId("not-a-real-app"))
    }

    @Test
    fun `launch command tokens are argv-safe`() {
        val safe = Regex("[A-Za-z0-9._/+%-]+")
        for (entry in CliAppCatalog.entries) {
            for (token in entry.launchCommand) {
                assertNotEquals("", token)
                assertTrue("token '$token' must be argv-safe", token.matches(safe))
            }
        }
    }

    @Test
    fun `installedCatalogApps maps only real apk answers in catalog order`() {
        // The device truth from 2026-09-02 09:10: nano installed, rest absent.
        val versions = mapOf(
            "nano" to "9.2-r0",
            "libintl" to "0.22.5-r0", // non-catalog package: never becomes a row
        )
        val apps = installedCatalogApps(versions)
        assertEquals(listOf("nano"), apps.map { it.entry.apkPackageName })
        assertEquals("9.2-r0", apps.single().version)
        assertEquals(CliAppCatalog.byId("nano"), apps.single().entry)
        // catalog order is preserved when several are installed
        val many = installedCatalogApps(
            mapOf("python3" to "3.12.10-r0", "nano" to "9.2-r0", "git" to "2.49.0-r0"),
        )
        assertEquals(listOf("nano", "git", "python3"), many.map { it.entry.apkPackageName })
        // empty real answer = honest empty list
        assertTrue(installedCatalogApps(emptyMap()).isEmpty())
    }
}
