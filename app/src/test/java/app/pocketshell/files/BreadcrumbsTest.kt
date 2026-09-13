package app.pocketshell.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7.2-A — breadcrumb derivation pins.
 *
 * What is pinned:
 *  - one crumb per ancestor, root FIRST, current location LAST;
 *  - the root crumb carries the AREA's label and navigates to "/";
 *  - every crumb path is a canonical, traversal-free [AreaPath] prefix of
 *    the browsed location (the same strings the core itself would compose);
 *  - exactly ONE crumb is the current one and it is the last;
 *  - arbitrary depth (the mission: "work for arbitrary nesting depth");
 *  - dotfile components (".env") are names like any other.
 */
class BreadcrumbsTest {

    private fun path(raw: String): AreaPath = PathSafety.validatePath(raw)!!

    @Test
    fun `area root renders exactly one current crumb labeled by the area`() {
        val crumbs = Breadcrumbs.of(path("/"), "Linux")
        assertEquals(1, crumbs.size)
        assertEquals("Linux", crumbs.first().name)
        assertEquals("/", crumbs.first().path.value)
        assertTrue(crumbs.first().isCurrent)
    }

    @Test
    fun `one level deep - root plus the folder`() {
        val crumbs = Breadcrumbs.of(path("/root"), "Linux")
        assertEquals(listOf("/", "/root"), crumbs.map { it.path.value })
        assertEquals(listOf("Linux", "root"), crumbs.map { it.name })
        assertFalse(crumbs[0].isCurrent)
        assertTrue(crumbs[1].isCurrent)
    }

    @Test
    fun `deep nesting - every ancestor is present in order`() {
        val crumbs = Breadcrumbs.of(path("/root/Projects/pocketshell/app/src"), "Linux")
        assertEquals(
            listOf("/", "/root", "/root/Projects", "/root/Projects/pocketshell", "/root/Projects/pocketshell/app", "/root/Projects/pocketshell/app/src"),
            crumbs.map { it.path.value },
        )
        assertEquals(
            listOf("Linux", "root", "Projects", "pocketshell", "app", "src"),
            crumbs.map { it.name },
        )
        assertTrue(crumbs.last().isCurrent)
        assertEquals(1, crumbs.count { it.isCurrent })
    }

    @Test
    fun `every crumb target is a valid canonical path (re-validates through PathSafety)`() {
        val crumbs = Breadcrumbs.of(path("/a/b/c/d/e/f/g"), "X")
        crumbs.forEach { crumb ->
            val validated = PathSafety.validatePath(crumb.path.value)
            assertEquals(crumb.path, validated)
        }
    }

    @Test
    fun `jumping several levels upward yields a valid prefix path`() {
        val deep = path("/root/Projects/pocketshell")
        val crumbs = Breadcrumbs.of(deep, "Linux")
        // Tap "Projects" (index 2) — the target must open that exact folder.
        val tapped = crumbs[2]
        assertEquals("/root/Projects", tapped.path.value)
        assertEquals("Projects", tapped.name)
        assertFalse(tapped.isCurrent)
    }

    @Test
    fun `dotfile components are ordinary names`() {
        val crumbs = Breadcrumbs.of(path("/root/.config"), "Linux")
        assertEquals(listOf("Linux", "root", ".config"), crumbs.map { it.name })
    }

    @Test
    fun `blank root label falls back to slash`() {
        val crumbs = Breadcrumbs.of(path("/root"), "")
        assertEquals("/", crumbs.first().name)
    }
}
