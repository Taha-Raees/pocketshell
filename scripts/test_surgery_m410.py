#!/usr/bin/env python3
"""m4.1.0 test surgery on CompanionTest.kt:
- remove the render-stall failure pin + all RenderProbe pins (block A)
- remove all BootWitness / CompanionHealth / ConsoleTail pins (block B)
- pin the retirement of the watchdog failure kinds
"""
import re

P = "/home/z/my-project/app/src/test/java/app/pocketshell/companion/CompanionTest.kt"
src = open(P).read()
lines = src.split("\n")

# --- Block A: from the @Test before `render-stall failure explains itself`
# through the line before `// ---- embedded-webview compat`.
a_start = next(i for i, l in enumerate(lines)
               if "render-stall failure explains itself" in l) - 1  # the @Test
a_end = next(i for i, l in enumerate(lines)
             if "// ---- embedded-webview compat" in l)
del lines[a_start:a_end]

src = "\n".join(lines)

# --- Block B: from `// ---- boot witness` through the last test before the
# class's closing brace. The class closes with a line that is exactly "}".
b_start = src.index("    // ---- boot witness")
tail = src[b_start:]
# class closing brace = last "}" at column 0
close = tail.rindex("\n}")
src = src[:b_start] + "\n}\n"

# --- Pin the retirement of the watchdog kinds (after the hints test).
anchor = """        assertTrue(
            CompanionFailure(CompanionFailureKind.RENDERER_GONE).hint.contains("Update or roll it back"),
        )
    }
"""
pin = anchor + """
    @Test
    fun `watchdog failure kinds are retired with the watchdog family`() {
        // m4.1.0: the m4.0.9 control experiment proved the render path the
        // pixel watchdog and boot witness policed was never the problem —
        // their failure kinds no longer exist to be raised.
        listOf("RENDER_STALLED", "APP_NOT_BOOTED").forEach { retired ->
            try {
                CompanionFailureKind.valueOf(retired)
                throw AssertionError("$retired should be retired")
            } catch (_: IllegalArgumentException) {
                // expected — the kind is gone
            }
        }
    }
"""
assert anchor in src, "hints-test anchor not found"
src = src.replace(anchor, pin, 1)

open(P, "w").write(src)
print("surgery ok; remaining CompanionFailureKind refs:")
for i, l in enumerate(src.split("\n"), 1):
    if "CompanionFailureKind." in l and "retired" not in l:
        print(f"  {i}: {l.strip()[:100]}")
