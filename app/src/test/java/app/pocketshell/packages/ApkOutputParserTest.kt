package app.pocketshell.packages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parser pins built from REAL apk-tools 3.0.6 output captured in the sandbox
 * rehearsal (scripts/rehearse_m24_packages.sh, Alpine 3.24.1 x86_64). If a
 * future apk changes output shape, these fixtures must be consciously
 * updated — never silently tolerated.
 */
class ApkOutputParserTest {

    @Test
    fun `parses rehearsal search lines`() {
        val output = """
            nano-9.2-r0
            nano-doc-9.2-r0
            nano-syntax-9.2-r0
            nanomsg-1.2.2-r0
            nanomsg-dev-1.2.2-r0
            nanomsg-doc-1.2.2-r0
            openvdb-nanovdb-13.0.0-r1
            openvdb-tools-13.0.0-r1
        """.trimIndent()
        val results = ApkOutputParser.parseSearch(output)
        assertEquals(8, results.size)
        assertEquals("nano", results[0].name)
        assertEquals("9.2", results[0].version)
        assertEquals("r0", results[0].release)
        // hyphens inside the name must not fool the splitter
        assertEquals("openvdb-nanovdb", results[6].name)
        assertEquals("13.0.0", results[6].version)
        assertEquals("r1", results[6].release)
    }

    @Test
    fun `parses versions with underscores and letters`() {
        // real line from the rehearsal: libncursesw-6.6_p20260516-r0
        val hit = ApkOutputParser.parseVersionLine("libncursesw-6.6_p20260516-r0")!!
        assertEquals("libncursesw", hit.name)
        assertEquals("6.6_p20260516", hit.version)
        assertEquals("r0", hit.release)
    }

    @Test
    fun `lines without a digit-starting version are skipped`() {
        assertNull(ApkOutputParser.parseVersionLine("WARNING: something"))
        assertNull(ApkOutputParser.parseVersionLine(""))
        assertNull(ApkOutputParser.parseVersionLine("just-a-name"))
        assertNull(ApkOutputParser.parseVersionLine("OK: 28645 distinct packages available"))
    }

    @Test
    fun `parseSearch skips junk but keeps valid lines`() {
        val results = ApkOutputParser.parseSearch("garbage-line\nnano-9.2-r0\n\nOK: done")
        assertEquals(1, results.size)
        assertEquals("nano", results[0].name)
    }

    @Test
    fun `info installed requires exit zero and a parsable line`() {
        val hit = ApkOutputParser.parseInfoInstalled(0, "nano-9.2-r0\n")
        assertTrue(hit.installed)
        assertEquals("9.2-r0", hit.version)

        // rehearsal: after `apk del`, exit code 1 and empty stdout
        val gone = ApkOutputParser.parseInfoInstalled(1, "")
        assertFalse(gone.installed)
        assertNull(gone.version)

        // exit 0 but unparseable output = cannot claim installed
        val weird = ApkOutputParser.parseInfoInstalled(0, "some unexpected line")
        assertFalse(weird.installed)
    }

    @Test
    fun `package name guard rejects argv-hostile input`() {
        assertTrue(ApkOutputParser.isValidPackageName("nano"))
        assertTrue(ApkOutputParser.isValidPackageName("python3"))
        assertTrue(ApkOutputParser.isValidPackageName("alpine-sdk"))
        assertTrue(ApkOutputParser.isValidPackageName("lib.ncurses+w"))
        assertFalse(ApkOutputParser.isValidPackageName(""))
        assertFalse(ApkOutputParser.isValidPackageName("-lead"))
        assertFalse(ApkOutputParser.isValidPackageName("a b"))
        assertFalse(ApkOutputParser.isValidPackageName("a;b"))
        assertFalse(ApkOutputParser.isValidPackageName("\$PATH"))
        assertFalse(ApkOutputParser.isValidPackageName("pack`age"))
    }

    @Test
    fun `rankSearchHits puts name matches ahead of description-only hits`() {
        // the exact device shape (screenshot 2026-09-02 10:04): searching
        // "node" returned ceph18/certbot-dns-linode/abseil-cpp-dev (matches in
        // the DESCRIPTION text) while nodejs itself was cut off by the limit
        val hits = listOf(
            PackageSearchResult("abseil-cpp-dev", "20250814.1", "r0"),
            PackageSearchResult("dpdk-node", "24.11.6", "r0"),
            PackageSearchResult("nodejs", "22.16.0", "r0"),
            PackageSearchResult("ceph18", "18.2.7", "r7"),
            PackageSearchResult("nodejs-current", "24.2.0", "r0"),
            PackageSearchResult("certbot-dns-linode", "5.6.0", "r0"),
        )
        val ranked = ApkOutputParser.rankSearchHits(hits, "node")
        assertEquals(
            listOf(
                "nodejs", "nodejs-current",       // name prefix matches, alphabetical
                "certbot-dns-linode", "dpdk-node", // name contains ("liNODE", "-NODE"), alphabetical
                "abseil-cpp-dev", "ceph18",        // description-only matches, alphabetical
            ),
            ranked.map { it.name },
        )
        // an exact-name query outranks everything
        assertEquals("nodejs", ApkOutputParser.rankSearchHits(hits, "nodejs").first().name)
        // blank query: returned unchanged (nothing to rank against)
        assertEquals(hits, ApkOutputParser.rankSearchHits(hits, "  "))
    }
}
