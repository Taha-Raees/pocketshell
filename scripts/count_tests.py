#!/usr/bin/env python3
"""Count tests/failures/errors across Gradle test-result XML dirs."""
import glob
import re
import sys

DIRS = [
    "app/build/test-results/testDebugUnitTest",
    "app/build/test-results/testReleaseUnitTest",
    "terminal-emulator/build/test-results/testDebugUnitTest",
    "terminal-emulator/build/test-results/testReleaseUnitTest",
]
base = "/home/z/my-project/"
grand = {"tests": 0, "failures": 0, "errors": 0}
for d in DIRS:
    t = f = e = 0
    for x in glob.glob(base + d + "/TEST-*.xml"):
        head = open(x, encoding="utf-8", errors="replace").read(2048)
        m = re.search(r'tests="(\d+)"[^>]*failures="(\d+)"[^>]*errors="(\d+)"', head) or \
            re.search(r'tests="(\d+)"', head)
        if not m:
            continue
        if m.lastindex and m.lastindex >= 3:
            t += int(m.group(1)); f += int(m.group(2)); e += int(m.group(3))
        else:
            t += int(m.group(1))
    print(f"{d}: tests={t} failures={f} errors={e}")
    grand["tests"] += t; grand["failures"] += f; grand["errors"] += e
print(f"TOTAL: tests={grand['tests']} failures={grand['failures']} errors={grand['errors']}")
sys.exit(0 if grand["failures"] == 0 and grand["errors"] == 0 else 1)
