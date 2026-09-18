package app.pocketshell.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M8 — registry invariants: unique ids, the historical default geometry,
 * honest summaries for the Control Center picker, and Missing resolution.
 */
class WidgetRegistryTest {

    @Test
    fun `ids are unique and well-shaped`() {
        val ids = WidgetRegistry.specs.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        ids.forEach { id ->
            assertTrue("id shape: $id", id.matches(Regex("""[a-z][a-z0-9.-]{1,63}""")))
        }
    }

    @Test
    fun `the two defaults are exactly the two core widgets`() {
        assertEquals(listOf(WidgetRegistry.TERMINAL_ID, WidgetRegistry.LINUX_ID), WidgetRegistry.DEFAULT_SLOTS)
        WidgetRegistry.DEFAULT_SLOTS.forEach { id ->
            assertTrue(WidgetRegistry.byId(id)!!.spec.isCore)
        }
        assertEquals(2, WidgetRegistry.specs.count { it.isCore })
    }

    @Test
    fun `the default weights reproduce the historical hero geometry`() {
        assertEquals(1.25f, WidgetRegistry.byId(WidgetRegistry.TERMINAL_ID)!!.spec.widthWeight)
        assertEquals(1.0f, WidgetRegistry.byId(WidgetRegistry.LINUX_ID)!!.spec.widthWeight)
        WidgetRegistry.specs.filter { !it.isCore }.forEach { spec ->
            assertEquals("non-core widgets are uniform width: ${spec.id}", 1.0f, spec.widthWeight)
        }
    }

    @Test
    fun `every widget carries picker-ready metadata`() {
        WidgetRegistry.specs.forEach { spec ->
            assertTrue(spec.name.isNotBlank())
            assertTrue(spec.summary.length in 5..80)
        }
    }

    @Test
    fun `unknown ids resolve as Missing - never substituted`() {
        val resolved = WidgetRegistry.resolve(listOf("core.terminal", "gone-widget"))
        assertTrue(resolved[0] is WidgetSlotEntry.Resolved)
        assertEquals("gone-widget", (resolved[1] as WidgetSlotEntry.Missing).widgetId)
    }

    @Test
    fun `the agents widget spec is not agent-vendor specific`() {
        // PocketShell is agent-agnostic: the widget describes coding agents
        // generically (AGENTS.md §17), never a single vendor.
        val summary = WidgetRegistry.specs.first { it.id == "agents" }.summary.lowercase()
        listOf("claude", "codex", "zcode", "cline", "kilo").forEach { vendor ->
            assertFalse("widget copy must not name a vendor: $vendor", summary.contains(vendor))
        }
    }
}
