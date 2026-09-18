package app.pocketshell.files

import app.pocketshell.files.saf.FileShareOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M8.3 — the Files sheet's "Install" action for .apk files: the name
 * detection and the MIME that routes the staged copy to the SYSTEM
 * package installer (ACTION_VIEW + package-archive), never a generic
 * binary hand-off.
 */
class FileShareOpsApkTest {

    @Test
    fun `apk names are detected case-insensitively`() {
        assertTrue(FileShareOps.isApkName("app.apk"))
        assertTrue(FileShareOps.isApkName("PocketShell.M8.3-L.APK"))
        assertTrue(FileShareOps.isApkName("x.y.ApK"))
        assertFalse(FileShareOps.isApkName("app.zip"))
        assertFalse(FileShareOps.isApkName("apk"))
        assertFalse(FileShareOps.isApkName("no-extension"))
    }

    @Test
    fun `apk files carry the package-archive mime - the installer's route`() {
        assertEquals(
            "application/vnd.android.package-archive",
            FileShareOps.guessMimeType("app.apk"),
        )
        assertEquals(
            "application/vnd.android.package-archive",
            FileShareOps.guessMimeType("PICTURE.APK"),
        )
    }

    @Test
    fun `non-apk files keep their own or the honest binary mime`() {
        assertEquals("text/plain", FileShareOps.guessMimeType("notes.txt"))
        assertEquals("application/zip", FileShareOps.guessMimeType("bundle.zip"))
        assertEquals(
            "application/octet-stream",
            FileShareOps.guessMimeType("firmware.bin"),
        )
    }
}
