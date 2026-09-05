#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PocketShell Platform / Runtime Forensic Audit - PDF report generator.
Route: pdf skill / Report brief (ReportLab body) + Template 07 cover (html2poster.js).
Chapter numbering plan (Step 3.5): cover=none, TOC=none, Chapter 1 = Executive Summary ... Chapter 19.
Palette: Template 07 "Crystal Blue" fixed body palette (documented template exception
to palette.cascade; see typesetting/cover.md Template 07 -> body palette table).
"""
import os, sys, hashlib

PDF_SKILL_DIR = "/home/z/my-project/skills/pdf"
sys.path.insert(0, os.path.join(PDF_SKILL_DIR, "scripts"))

from reportlab.lib.pagesizes import A4
from reportlab.lib.units import inch
from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT, TA_JUSTIFY, TA_CENTER
from reportlab.lib.styles import ParagraphStyle
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfbase.pdfmetrics import registerFontFamily, stringWidth
from reportlab.platypus import (SimpleDocTemplate, Paragraph, Spacer, PageBreak,
                                Table, TableStyle, KeepTogether, CondPageBreak,
                                Preformatted, Flowable, HRFlowable)
from reportlab.platypus.tableofcontents import TableOfContents

FONT_DIR = "/usr/share/fonts"
pdfmetrics.registerFont(TTFont("NotoSerifSC", f"{FONT_DIR}/truetype/noto-serif-sc/NotoSerifSC-Regular.ttf"))
pdfmetrics.registerFont(TTFont("NotoSerifSC-Bold", f"{FONT_DIR}/truetype/noto-serif-sc/NotoSerifSC-Bold.ttf"))
# NOTE: this sandbox ships NotoSansSC only as a variable font, which ReportLab
# cannot parse; alias the "Noto Sans SC" fallback-chain slot to NotoSerifSC
# (static files). The document is English - the CJK fallback is a safety net.
pdfmetrics.registerFont(TTFont("Noto Sans SC", f"{FONT_DIR}/truetype/noto-serif-sc/NotoSerifSC-Regular.ttf"))
pdfmetrics.registerFont(TTFont("Noto Sans SC Bold", f"{FONT_DIR}/truetype/noto-serif-sc/NotoSerifSC-Bold.ttf"))
pdfmetrics.registerFont(TTFont("FreeSerif", f"{FONT_DIR}/truetype/freefont/FreeSerif.ttf"))
pdfmetrics.registerFont(TTFont("FreeSerif-Bold", f"{FONT_DIR}/truetype/freefont/FreeSerifBold.ttf"))
pdfmetrics.registerFont(TTFont("FreeSerif-Italic", f"{FONT_DIR}/truetype/freefont/FreeSerifItalic.ttf"))
pdfmetrics.registerFont(TTFont("FreeSerif-BoldItalic", f"{FONT_DIR}/truetype/freefont/FreeSerifBoldItalic.ttf"))
pdfmetrics.registerFont(TTFont("DejaVuSans", f"{FONT_DIR}/truetype/dejavu/DejaVuSansMono.ttf"))
registerFontFamily("NotoSerifSC", normal="NotoSerifSC", bold="NotoSerifSC-Bold")
registerFontFamily("Noto Sans SC", normal="Noto Sans SC", bold="Noto Sans SC Bold")
registerFontFamily("FreeSerif", normal="FreeSerif", bold="FreeSerif-Bold",
                   italic="FreeSerif-Italic", boldItalic="FreeSerif-BoldItalic")
registerFontFamily("DejaVuSans", normal="DejaVuSans", bold="DejaVuSans")

from pdf import install_font_fallback
install_font_fallback()

# ---- Template 07 Crystal Blue body palette (fixed by typesetting/cover.md) ----
PAGE_BG      = colors.HexColor("#f5f8fc")   # XL
SECTION_BG   = colors.HexColor("#edf2f9")   # XL
CARD_BG      = colors.HexColor("#e4ecf5")   # L
TABLE_STRIPE = colors.HexColor("#eef3fa")   # L
HEADER_FILL  = colors.HexColor("#1a4a7a")   # M  (table headers, H2 color)
BORDER       = colors.HexColor("#c0d0e2")   # S
ACCENT       = colors.HexColor("#2d7ab3")   # XS
TEXT_PRIMARY = colors.HexColor("#142840")
TEXT_MUTED   = colors.HexColor("#5a7a96")
C_ACC   = "#2d7ab3"   # OBSERVED FACT
C_TXT   = "#142840"   # INFERENCE
C_HDR   = "#1a4a7a"   # RECOMMENDATION
C_MUT   = "#5a7a96"   # UNKNOWN

MARGIN = 0.9 * inch
PAGE_W, PAGE_H = A4
AVAIL_W = PAGE_W - 2 * MARGIN
AVAIL_H = PAGE_H - 2 * MARGIN
# SimpleDocTemplate.build() creates its First/Later frames with the default
# 6pt Frame padding on every side - compensate so tables/code never overflow.
CONTENT_W = AVAIL_W - 12
H1_ORPHAN = AVAIL_H * 0.25
MAX_CODE_W = 88  # mono chars per line at 7.8pt DejaVuSansMono

DOC_TITLE = "PocketShell Platform / Runtime Forensic Audit"

S = {}
S["body"] = ParagraphStyle("Body", fontName="FreeSerif", fontSize=10.2, leading=15.2,
                           alignment=TA_JUSTIFY, textColor=TEXT_PRIMARY, spaceAfter=7)
S["bodyL"] = ParagraphStyle("BodyL", parent=S["body"], alignment=TA_LEFT)
S["h1"] = ParagraphStyle("H1", fontName="FreeSerif", fontSize=19, leading=24,
                         textColor=TEXT_PRIMARY, spaceBefore=16, spaceAfter=2)
S["h2"] = ParagraphStyle("H2", fontName="FreeSerif", fontSize=13.5, leading=18,
                         textColor=HEADER_FILL, spaceBefore=13, spaceAfter=5)
S["h3"] = ParagraphStyle("H3", fontName="FreeSerif", fontSize=11.2, leading=15,
                         textColor=TEXT_PRIMARY, spaceBefore=10, spaceAfter=4)
S["bullet"] = ParagraphStyle("Bullet", parent=S["bodyL"], leftIndent=16, bulletIndent=4,
                             spaceAfter=4)
S["num"] = ParagraphStyle("Num", parent=S["bodyL"], leftIndent=20, firstLineIndent=-20,
                          spaceAfter=5)
S["cell"] = ParagraphStyle("Cell", fontName="FreeSerif", fontSize=8.6, leading=11.6,
                           textColor=TEXT_PRIMARY, alignment=TA_LEFT)
S["cellC"] = ParagraphStyle("CellC", parent=S["cell"], alignment=TA_CENTER)
S["cellH"] = ParagraphStyle("CellH", fontName="FreeSerif", fontSize=8.8, leading=11.8,
                            textColor=colors.white, alignment=TA_LEFT)
S["cellHC"] = ParagraphStyle("CellHC", parent=S["cellH"], alignment=TA_CENTER)
S["code"] = ParagraphStyle("Code", fontName="DejaVuSans", fontSize=7.8, leading=10.2,
                           textColor=TEXT_PRIMARY)
S["cap"] = ParagraphStyle("Cap", fontName="FreeSerif", fontSize=8.5, leading=11,
                          textColor=TEXT_MUTED, alignment=TA_CENTER,
                          spaceBefore=3, spaceAfter=6)
S["quote"] = ParagraphStyle("Quote", fontName="FreeSerif-Italic", fontSize=10.2,
                            leading=15, leftIndent=24, textColor=TEXT_PRIMARY,
                            spaceBefore=6, spaceAfter=8)
S["toc0"] = ParagraphStyle("TOC0", fontName="FreeSerif-Bold", fontSize=10.5, leading=16,
                           textColor=TEXT_PRIMARY, leftIndent=4)
S["toc1"] = ParagraphStyle("TOC1", fontName="FreeSerif", fontSize=9.5, leading=14,
                           textColor=TEXT_MUTED, leftIndent=22)
S["tocTitle"] = ParagraphStyle("TocTitle", fontName="FreeSerif", fontSize=17, leading=22,
                               textColor=TEXT_PRIMARY, spaceAfter=10)

LABELS = {
    "OBS": ("OBSERVED FACT", C_ACC),
    "INF": ("INFERENCE", C_TXT),
    "REC": ("RECOMMENDATION", C_HDR),
    "UNK": ("UNKNOWN", C_MUT),
}

def lbl(tag):
    name, col = LABELS[tag]
    return f'<font color="{col}"><b>{name}</b></font> -- '

def cite(ref):
    return f'<font name="DejaVuSans" size="8">{ref}</font>'

def mono(t):
    return f'<font name="DejaVuSans" size="8.6">{t}</font>'

class BodyStartMarker(Flowable):
    """Records the page where chapter 1 begins (for roman/arabic footer split)."""
    def __init__(self, state):
        Flowable.__init__(self)
        self.state = state
        self.width = self.height = 0
    def draw(self):
        self.state["body_start"] = self.canv.getPageNumber()

PAGE_STATE = {"body_start": None}

_ROMAN = ["i","ii","iii","iv","v","vi","vii","viii","ix","x","xi","xii"]

def _footer(cv, doc):
    cv.saveState()
    # full-page background tint (Template 07 body palette)
    cv.setFillColor(PAGE_BG)
    cv.rect(0, 0, PAGE_W, PAGE_H, fill=1, stroke=0)
    # header
    cv.setFont("FreeSerif", 7.5)
    cv.setFillColor(TEXT_MUTED)
    cv.drawString(MARGIN, PAGE_H - 0.55 * inch, DOC_TITLE)
    cv.setStrokeColor(ACCENT)
    cv.setLineWidth(1.2)
    cv.line(MARGIN, PAGE_H - 0.62 * inch, PAGE_W - MARGIN, PAGE_H - 0.62 * inch)
    # footer
    cv.setStrokeColor(BORDER)
    cv.setLineWidth(0.5)
    cv.line(MARGIN, 0.62 * inch, PAGE_W - MARGIN, 0.62 * inch)
    cv.setFont("FreeSerif", 7.5)
    cv.setFillColor(TEXT_MUTED)
    cv.drawString(MARGIN, 0.45 * inch, "PocketShell Engineering - read-only forensic audit")
    bs = PAGE_STATE["body_start"]
    page = cv.getPageNumber()
    if bs is None or page < bs:
        num = _ROMAN[page - 1] if page - 1 < len(_ROMAN) else str(page)
    else:
        num = str(page - bs + 1)
    cv.drawRightString(PAGE_W - MARGIN, 0.45 * inch, num)
    cv.restoreState()

class TocDocTemplate(SimpleDocTemplate):
    def afterFlowable(self, flowable):
        if hasattr(flowable, "bookmark_name"):
            level = getattr(flowable, "bookmark_level", 0)
            text = getattr(flowable, "bookmark_text", "")
            key = getattr(flowable, "bookmark_key", "")
            # Record the PRINTED body page number (arabic reset at chapter 1),
            # so TOC references match the footers exactly.
            bs = PAGE_STATE["body_start"] or self.page
            self.notify("TOCEntry", (level, text, self.page - bs + 1, key))

def heading(text, style, level):
    key = "h_" + hashlib.md5(text.encode()).hexdigest()[:8]
    p = Paragraph(f'<a name="{key}"/><b>{text}</b>', style)
    p.bookmark_name = key
    p.bookmark_level = level
    p.bookmark_text = text
    p.bookmark_key = key
    return p

def rule():
    return HRFlowable(width="100%", thickness=1.6, color=ACCENT, spaceBefore=1, spaceAfter=9)

def make_table(header, rows, ratios, align_center=None):
    align_center = align_center or []
    data = []
    if header:
        hrow = []
        for i, h in enumerate(header):
            st = S["cellHC"] if i in align_center else S["cellH"]
            hrow.append(Paragraph(f"<b>{h}</b>", st))
        data.append(hrow)
    for r in rows:
        row = []
        for i, c in enumerate(r):
            st = S["cellC"] if i in align_center else S["cell"]
            row.append(Paragraph(str(c), st))
        data.append(row)
    widths = [ratio * AVAIL_W for ratio in ratios]
    t = Table(data, colWidths=widths, hAlign="CENTER",
              repeatRows=1 if header else 0)
    style = [
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("GRID", (0, 0), (-1, -1), 0.5, BORDER),
        ("LEFTPADDING", (0, 0), (-1, -1), 5),
        ("RIGHTPADDING", (0, 0), (-1, -1), 5),
        ("TOPPADDING", (0, 0), (-1, -1), 4),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
    ]
    if header:
        style.append(("BACKGROUND", (0, 0), (-1, 0), HEADER_FILL))
        start = 1
    else:
        start = 0
    for i in range(start, len(data)):
        if (i - start) % 2 == 1:
            style.append(("BACKGROUND", (0, i), (-1, i), TABLE_STRIPE))
        else:
            style.append(("BACKGROUND", (0, i), (-1, i), colors.white))
    t.setStyle(TableStyle(style))
    return t

def code_block(text, caption=None):
    lines = []
    for raw in text.split("\n"):
        raw = raw.rstrip()
        while stringWidth(raw, "DejaVuSans", S["code"].fontSize) > CONTENT_W - 18:
            raw = raw[:-1]
        lines.append(raw)
    pre = Preformatted("\n".join(lines), S["code"])
    inner = Table([[pre]], colWidths=[CONTENT_W * 0.97])
    inner.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), TABLE_STRIPE),
        ("LINEBEFORE", (0, 0), (0, -1), 2.2, ACCENT),
        ("LEFTPADDING", (0, 0), (-1, -1), 10),
        ("RIGHTPADDING", (0, 0), (-1, -1), 8),
        ("TOPPADDING", (0, 0), (-1, -1), 7),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
    ]))
    inner.hAlign = "CENTER"
    out = [Spacer(1, 6), inner]
    if caption:
        out += [Paragraph(caption, S["cap"])]
    out += [Spacer(1, 6)]
    return out

def stat_row(pairs):
    """pairs: list of (big_value, small_label) - rendered as metric cards."""
    n = len(pairs)
    cw = CONTENT_W * 0.97 / n
    top = [Paragraph(f'<font color="{C_HDR}"><b>{v}</b></font>',
                     ParagraphStyle("Stat", fontName="FreeSerif", fontSize=13,
                                    leading=16, alignment=TA_CENTER)) for v, _ in pairs]
    bot = [Paragraph(l, ParagraphStyle("StatL", fontName="FreeSerif", fontSize=7.6,
                                       leading=10, textColor=TEXT_MUTED,
                                       alignment=TA_CENTER)) for _, l in pairs]
    t = Table([top, bot], colWidths=[cw] * n, hAlign="CENTER")
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), CARD_BG),
        ("BOX", (0, 0), (-1, -1), 0.8, BORDER),
        ("LINEBEFORE", (1, 0), (-1, -1), 0.5, BORDER),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("TOPPADDING", (0, 0), (-1, 0), 8),
        ("BOTTOMPADDING", (0, 1), (-1, 1), 8),
        ("TOPPADDING", (0, 1), (-1, 1), 1),
        ("BOTTOMPADDING", (0, 0), (-1, 0), 1),
    ]))
    return [Spacer(1, 8), t, Spacer(1, 10)]

def callout(title, text):
    body = Paragraph(f'<font color="{C_HDR}"><b>{title}</b></font> -- {text}',
                     ParagraphStyle("Callout", parent=S["bodyL"], spaceAfter=0))
    t = Table([[body]], colWidths=[CONTENT_W * 0.97])
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), CARD_BG),
        ("LINEBEFORE", (0, 0), (0, -1), 3, ACCENT),
        ("LEFTPADDING", (0, 0), (-1, -1), 10),
        ("RIGHTPADDING", (0, 0), (-1, -1), 10),
        ("TOPPADDING", (0, 0), (-1, -1), 8),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 8),
    ]))
    t.hAlign = "CENTER"
    return [Spacer(1, 5), t, Spacer(1, 8)]

BLOCKS = []

# ============================ CHAPTER 1 ============================
BLOCKS += [
("h1", "1. Executive Summary"),
("p", "This report is a read-only forensic audit of the PocketShell repository at " +
      mono("/home/z/my-project") + ", performed entirely by static code inspection, git-history analysis, and "
      "documentation mining. No code was modified, nothing was installed, no dependency or build configuration "
      "was touched. The audited state is branch " + mono("main") + ", HEAD " + mono("7364452") + " (code tip "
      + mono("afdf37a") + "), versionName " + mono("0.9.1-m5.1.0") + ", versionCode 39, with 205 commits and 752 "
      "green JVM tests. It is designed to be merged with a companion forensic report produced from inside the "
      "running Linux guest; every claim here is traceable to a file, a line, a document section, or a recorded "
      "device incident, and is labeled " + lbl("OBS") + lbl("INF") + lbl("REC") + " or " + lbl("UNK") +
      " throughout."),
("p", "The platform verdict in one paragraph: PocketShell is an Android-native single-activity Kotlin/Compose "
      "application that embeds a vendored Termux terminal engine (GPLv3, " +
      mono("com.termux.terminal") + " and " + mono("com.termux.view") + "), allocates real PTYs through an "
      "unchanged JNI (" + mono("termux.c") + "), and runs an Alpine Linux 3.24.1 (musl, aarch64) userspace "
      "under a self-compiled Termux PRoot fork (v5.1.107.92 @ " + mono("7266fb3e") + ", GPL-2.0) shipped as "
      + mono("libproot.so") + " in the APK's native library directory. The app deliberately pins targetSdk 28 "
      "because only the legacy " + mono("untrusted_app_27") + " SELinux domain may " + mono("execve()") +
      " files out of app-private storage - the exact trade Termux makes, and the single load-bearing fact of "
      "the whole architecture. Package management is real apk inside the guest, hardened by a one-byte "
      "fd-link patch and a pattern-based self-repair, with /proc bound unconditionally to interactive "
      "sessions since m3.6. The Companion web runtime is a frozen, contract-pinned baseline renderer with one "
      "WebView per open tab."),
("stats", [("22.57 MB", "APK (vc39, sha256 d8084b49...)"),
           ("4.02 MB / 9.3 MB", "Alpine rootfs: download / extracted"),
           ("2000 rows", "terminal scrollback bound per session"),
           ("752", "JVM tests green at vc39")]),
("h2", "1.1 Top findings"),
("n", [
  "<b>F0 - Architecture strength (no severity).</b> The layering is clean and honest: UI, session manager, "
  "vendored terminal, runtime installer, launch-profile builder, package gateway, and Companion host are "
  "separate units with pinned contracts and tests. Startup is lazy (no webkit, no guest work at "
  "Application.onCreate), failures are surfaced instead of faked, and every trust-critical artifact is "
  "sha256-pinned. The M5.1 audit (commit afdf37a) already confirmed the core paths sound and applied four "
  "targeted fixes (F1-F4).",
  "<b>F1 - Biggest architectural risk: single-libc (musl-only) guest.</b> Modern prebuilt ARM64 developer "
  "CLIs (Node.js official builds, npm-distributed AI coding agents, sanitizer-embedded binaries) overwhelmingly "
  "target glibc. " + lbl("OBS") + " The recorded on-device ladder proves gcompat is a dead end for this class: "
  "the Antigravity CLI failed on missing glibc-private symbols and then SIGSEGV'd inside its embedded sanitizer "
  "runtime even with a four-symbol shim (docs/ANTIGRAVITY-PLATFORM.md sections 2-4). " + lbl("REC") +
  " Ship a minimal glibc sidecar prefix under PocketShell's control (Runtime 2.0, chapter 17).",
  "<b>F2 - PRoot is the right tool and its overhead is the accepted tax.</b> " + lbl("OBS") + " PRoot provides "
  "path translation, the uid=0 illusion, and bind emulation via ptrace with no root privileges; every bind is "
  "per-session and dies with it. Its syscall interception adds measurable overhead to syscall-heavy workloads "
  "(compiles, git on large trees). " + lbl("INF") + " This cost is bounded and acceptable at PocketShell's "
  "scale; it should be measured (microbenchmark) rather than engineered around. " + lbl("REC") +
  " Keep the pinned fork; do not rewrite.",
  "<b>F3 - Companion memory is unbounded by deliberate policy.</b> " + lbl("OBS") + " One live WebView per "
  "open tab, tabs live until the user closes them, no LRU, no saveState/restore (retired at m4.0.11 as the "
  "render-breaking suspect family). The policy is CPU-safe (M5.1 F1 pauses everything on minimize) but "
  "memory-unbounded on 4-6 GB devices. " + lbl("REC") + " Measure first (TESTING section 32.2E is still "
  "pending), then introduce a device-RAM-class tab budget with user-visible guidance - not silent eviction.",
  "<b>F4 - Terminal correctness gap in the ctrl/alt character path.</b> " + lbl("OBS") +
  " PocketShellTerminalViewClient.onCodePoint always returns true, which makes upstream TerminalView's full "
  "ctrl transliteration (Ctrl+3..8, Ctrl+[ / \\ / ], Ctrl+_, alt-prefix ESC) dead code; the replacement map is "
  "reduced and drops Alt+letter composition (PocketShellTerminalViewClient.kt:46-58). " + lbl("REC") +
  " Return false for unmapped combos or extend the map; small, low-risk fix.",
  "<b>F5 - Session model is linear, honest, and leak-free by static inspection.</b> " + lbl("OBS") +
  " One PTY + three Java threads + bounded 2000-row buffer per session; one proot per Linux session; "
  "zombie-free via dedicated waitpid threads; foreground service stops itself when the last session closes; "
  "process death (LMK) loses sessions by documented design. " + lbl("REC") + " Keep; add a soft session-count "
  "guard (8-10) with an honest warning rather than a hard cap.",
  "<b>F6 - Startup is already lazy; do not optimize.</b> " + lbl("OBS") + " Application.onCreate touches no "
  "webkit, spawns no guest work, and the m4.0.1 rule (section 22) is enforced in code and honored by M5.1 F4's "
  "gated CookieManager flush. Listed as a finding only to freeze it as a regression guard.",
  "<b>F7 - The delivery chain is part of the architecture.</b> " + lbl("OBS") + " Cut-at-tip payload ritual, "
  "three-way mirror, pinned debug keystore, byte-identical rebuild recovery (sandbox resets #1-#12), and "
  "explicit-name withdrawal mirrors. " + lbl("REC") + " Preserve when Runtime 2.0 adds new payload classes.",
  "<b>F8 - targetSdk 28 is load-bearing.</b> " + lbl("OBS") + " Raising it silently kills the guest "
  "(execute_no_trans neverallow for targetSdk >= 29 domains). " + lbl("REC") + " Never bump without a new "
  "execution story (chapter 6); add a build-time guard comment already present in app/build.gradle.kts:16-24.",
]),
("h2", "1.2 What to do next (decision-level, not implementation)"),
("p", "First, run the pending device measurement gates (TESTING.md section 32 scenarios A-G) so the M5.1 "
      "fixes have before/after numbers and the Companion tab-cost model stops being inferential. Second, "
      "decide the glibc strategy against the companion Kilo/MiniMax guest-side report: this audit recommends "
      "a minimal glibc-aarch64 sidecar prefix owned by PocketShell, provisioned like the rootfs (pinned, "
      "sha256-verified, downloadable), with a per-ELF-interpreter launcher so musl and glibc binaries "
      "coexist invisibly (chapters 8 and 17). Third, apply the two small terminal fixes (ctrl-map gap, "
      "cursor-hidden blinker) inside the next regular phase - they are correctness items, not a redesign. "
      "Nothing in this report requires replacing PRoot, changing distribution, or touching the frozen "
      "Companion renderer."),

# ============================ CHAPTER 2 ============================
("h1", "2. Repository Architecture and Provenance"),
("h2", "2.1 Repository and build facts"),
("table",
 ["Field", "Value", "Evidence"],
 [
  ["Branch / HEAD", "main / 736445252b8b89dd3703144c873d3f37f91ee184 (code tip afdf37a)", "git rev-parse, git log"],
  ["History", "205 commits, no tags; version chain vc16-vc39 on one pinned debug cert (d96a6f66...)", "git rev-list --count; worklog"],
  ["versionName / versionCode", "0.9.1-m5.1.0 / 39", "app/build.gradle.kts:25-26"],
  ["SDK matrix", "minSdk 26, targetSdk 28 (deliberate), compileSdk 36", "app/build.gradle.kts:15-27"],
  ["Gradle modules", ":app, :terminal-emulator (vendored GPLv3), :terminal-view (vendored GPLv3)", "settings.gradle.kts:25-27"],
  ["Native code", "termux.c JNI (PTY) built by NDK r28c 28.2.13676358; proot stack shipped as prebuilt .so in jniLibs (4 ABIs)", "terminal-emulator/src/main/jni; app/src/main/jniLibs"],
  ["jniLibs packaging", "useLegacyPackaging = true (extractNativeLibs) so path-based execve() of libproot.so works; the v0.3.0 device crash cause", "app/build.gradle.kts:74-87"],
  ["Signing", "debug keystore pinned in repo (keystore/debug.keystore) - update identity for side-loaded APKs", "app/build.gradle.kts:46-53"],
  ["Key Android deps", "Compose BOM 2026.08.00, lifecycle 2.11.0, datastore-preferences 1.2.1, kotlinx-serialization 1.9.0, commons-compress 1.28.0, junit 4.13.2", "docs/THIRD_PARTY.md:68-85"],
  ["Manifest permissions", "INTERNET, ACCESS_NETWORK_STATE, FOREGROUND_SERVICE(_SPECIAL_USE), POST_NOTIFICATIONS - nothing else", "AndroidManifest.xml:22-26"],
 ],
 [0.22, 0.55, 0.23]),
("h2", "2.2 Application package architecture"),
("p", "The " + mono("app.pocketshell") + " package is organized as ten coherent subsystems. " + lbl("OBS") +
      " The map below is derived from the source tree; it doubles as the dependency map because each layer "
      "only calls downward."),
("table",
 ["Package", "Role", "Key units"],
 [
  ["ui/ (home, terminal, companion, apps, settings, system, diagnostics, theme)", "Compose UI; single activity, persistent Companion overlay above every screen", "HomeScreen, TerminalScreen, CompanionLayer, CompanionTabStrip, SettingsScreen, DiagnosticsScreen, Theme.kt"],
  ["terminal/", "Session ownership + clients + environment", "TerminalSessionManager (process-scoped singleton), PocketShellSessionClient, PocketShellTerminalViewClient, ShellEnvironment, TerminalService (specialUse FGS)"],
  ["keyboard/", "The one custom keyboard (no system IME anywhere)", "TerminalKeyboard, KeyLayouts, KeyboardState, TerminalKeyDispatcher, KeyboardInputRouter"],
  ["runtime/", "Linux environment lifecycle", "RuntimeManager, RuntimeInstaller, RuntimeProcessLauncher, RuntimeStorage/Checksum/Metadata/State/CrashGuard/Diagnostics, GuestEnvironment, GuestApkCompat, GuestSysDataCompat"],
  ["packages/", "apk gateway + honest package state machine", "PackageGateway, AlpinePackageManager, PackageOperationManager, GuestCommandRunner, PackageManager, PackageModels, CliAppCatalog"],
  ["apps/", "Command-launchable CLI apps", "CommandApps (seed registry of 9 agents), availability probing, launch chain"],
  ["companion/ + ui/companion/", "Frozen web runtime", "CompanionWebHost (WebView registry), CompanionTabs, CompanionRepository, CompanionModels, CompanionRenderContract, WebCompat (dead code), CompanionViewModel"],
  ["settings/ + diagnostics/", "DataStore settings, read-mostly facts", "SettingsRepository, SettingsViewModel, Diagnostics"],
  ["root files", "Entry + view model", "MainActivity (single task, configChanges, IME hard-block), PocketShellApp, TerminalViewModel"],
 ],
 [0.26, 0.30, 0.44]),
("h2", "2.3 Open-source foundation (Phase B) - exact provenance"),
("p", "Every third-party component, its pin, license, distribution form, modifications, and update strategy. " +
      lbl("OBS") + " All rows verified against docs/THIRD_PARTY.md, build scripts, and shipped artifacts. "
      "Note the license consequence: vendoring GPLv3-only terminal code makes PocketShell as a whole "
      "GPL-3.0-only, an explicit M0 decision."),
("table",
 ["Component", "Pin / version", "License", "Form", "Modifications", "Update strategy"],
 [
  ["Termux terminal-emulator", "termux-app @ 3b66f879 (2026-08-24)", "GPL-3.0-only", "Vendored module :terminal-emulator", "None to .java/.c/.mk; build script ported to kts; minSdk 21->26", "Re-sync procedure documented (THIRD_PARTY.md): fetch, copy, diff, run upstream tests, device checklist"],
  ["Termux terminal-view", "same snapshot 3b66f879", "GPL-3.0-only", "Vendored module :terminal-view", "None to sources", "Same re-sync procedure"],
  ["PRoot", "termux/proot tag v5.1.107.92 @ 7266fb3e (proot 5.1.0 + Termux patches)", "GPL-2.0", "Compiled from pinned source into jniLibs (libproot.so + libproot-loader.so + loader32)", "2 build micro-patches: string.h include in ashmem_memfd.c (bionic/clang 16 strictness); mawk-compatible loader-info.awk", "Rebuild via scripts/build_proot_m23.sh; bump pin deliberately; tests pin argv shape"],
  ["libtalloc", "2.4.2", "LGPL-3.0-or-later", "Compiled, linked dynamically, shipped per ABI as libtalloc.so (SONAME normalized)", "SONAME byte-patch; canned cross-answers for waf", "Bump pin; source archive obligation met"],
  ["Alpine minirootfs", "alpine-minirootfs-3.24.1-aarch64.tar.gz, 4,023,732 B, sha256 f55a90f6...1259", "Alpine packages under their OSS licenses (musl MIT, busybox GPL-2.0)", "Downloaded at install, size+sha256 verified", "None; extracted with commons-compress under zip-slip guard", "Bump pin = RuntimeManager constants + RuntimeChecksum; guest apk upgrade is allowed and self-heals via GuestApkCompat"],
  ["apk-tools (guest)", "3.0.6-r0 (literal layout verified identical in 3.0.8)", "GPL-2.0", "One-byte fd-link-patched libapk shipped as asset + pattern self-repair in guest", "Flip '/proc/self/fd' literal -> '/proc/self/fX' (is_proc_fd_ok gate)", "Pattern-based scan (gate + format literals) - version independent by design"],
  ["JetBrains Mono NL", "v2.304", "OFL-1.1", "3 TTFs bundled (terminal typeface, no ligatures)", "None", "Redistribution of unmodified files"],
  ["commons-compress", "1.28.0", "Apache-2.0", "Gradle dependency", "None", "Version catalog"],
  ["androidx / kotlin stack", "BOM 2026.08.00 etc.", "Apache-2.0", "Gradle dependencies", "None", "Version catalog"],
  ["Termux PRoot-Distro", "5.8.0 @ f832a56 (studied, NOT copied)", "GPL-3.0", "Architecture reference only", "GuestSysDataCompat is an original, narrower Kotlin implementation", "Watch upstream for sysdata overlay set changes"],
 ],
 [0.13, 0.19, 0.11, 0.16, 0.22, 0.19]),
("h2", "2.4 Delivery pipeline"),
("p", "The payload system is a first-class subsystem. " + lbl("OBS") + " " + mono("scripts/make_payload_m2.sh") +
      " cuts, from the pinned code tip: a full git bundle, a source zip/tgz pair (zero dotfiles, no web "
      "scaffold), a RESTORE.txt with per-version history, and a copy of the pre-built APK - then verifies "
      "per-file presence pins and prints a sha256 summary. Per-version mirror scripts (" +
      mono("mirror_m4011.sh") + " through " + mono("mirror_m5110.sh") + ") withdraw stale cuts by explicit "
      "name and maintain a three-way mirror (download/ == public/ == dist-master/) with HTTP re-verification. "
      "Zip/tgz are not byte-reproducible (embedded timestamps); the git bundle and APK are deterministic. "
      "This discipline exists because the sandbox was wiped twelve times; the ritual is what makes every "
      "recovery a byte-identical rebuild rather than a new version."),
]

# ============================ CHAPTER 3 ============================
BLOCKS += [
("h1", "3. Runtime Architecture: The Linux Launch Chain"),
("h2", "3.1 Sequence diagram (exact, traced through code)"),
("p", lbl("OBS") + " The following is the complete chain for every guest session, traced from the UI to "
      "execve. It is lazy by design: the process is forked only when the session's view first lays out, "
      "which is precisely why the m3.4 argv-launch fix exists (delivering a launch command through argv, "
      "never through a PTY write that can race " + mono("mShellPid == 0") + ")."),
("code",
"""User (Home: "Linux Shell" / command app tile / catalog Open)
 |
 v  TerminalViewModel.openLinuxShell / openCommandApp      [main thread]
 |    gate: RuntimeProcessLauncher.canEnterLinuxShell(state==READY)
 v  TerminalSessionManager.createLinuxSessionInternal      [TerminalSessionManager.kt:160-209]
 |    PackageGateway.prepareGuestForSession(...)           [synchronous repair]
 |      - GuestEnvironment.ensureDnsResolvers  (device DNS + fallback, musl MAXNS=3, marker)
 |      - GuestEnvironment.ensureApkWorkspace (tmp 1777, apk cache dirs)
 |      - GuestApkCompat.ensure           (pattern scan; flip '/proc/self/fd'->'/proc/self/fX' if repairable)
 |      - GuestSysDataCompat.prepare      (probe-first overlays for 5 kernel-denied /proc files)
 |    spec = RuntimeProcessLauncher.buildSessionSpec(profile=INTERACTIVE_TERMINAL)
 |    spec audited: procContractProblem(spec) - missing /proc|/dev|/sys bind throws  [fail-loud]
 |    spawn(): TerminalSession(proot argv, env, cwd) - CONSTRUCTOR ONLY STORES (no fork yet)
 |    syncService(): startForegroundService(TerminalService)  [first session -> FGS]
 v  user navigates to Terminal tab -> TerminalViewHost (AndroidView factory)
 |    view.attachSession(entry.session)                    [TerminalScreen.kt:503]
 v  TerminalSession.updateSize(w,h)  (first real layout)
 |    initializeEmulator: TerminalEmulator(TerminalBuffer 2000 rows)
 |    mTerminalFileDescriptor = JNI.createSubprocess(...)   [main thread; termux.c]
 |      open("/dev/ptmx", O_RDWR|O_CLOEXEC); grantpt/unlockpt/ptsname_r
 |      termios: IUTF8 on, IXON|IXOFF off (Ctrl+S lockup prevention); TIOCSWINSZ initial
 |      fork() -> child: sigfillset+SIG_UNBLOCK; close(ptm); setsid(); open pts; dup2 0/1/2
 |                close every fd>2 (scan /proc/self/fd); clearenv()+putenv(env); chdir(cwd)
 |                execvp(argv[0]=<nativeLibraryDir>/libproot.so, argv)
 v  proot (bionic, LD_LIBRARY_PATH=<nativeLibraryDir>, PROOT_LOADER=libproot-loader.so)
 |    PTRACE_TRACEME children; ptrace syscall interception: path translation into rootfs
 |    binds (this process tree only, no real mounts): /dev /proc [+5 sysdata file binds] /sys
 |      + apk-cache binds + --root-id (uid 0 illusion) + --cwd=/root + --link2symlink
 |    exec guest command: /bin/sh -l   (busybox ash login shell on musl)
 v  interactive PTY loop
    [out] child -> pty master -> reader thread -> ByteQueue(64 KiB) -> MainThreadHandler
          -> TerminalEmulator.append (parse) -> onTextChanged -> {TerminalView.invalidate (visible only, M5.1 F3)}
    [in]  keyboard -> KeyEvent -> KeyHandler -> session.write -> ByteQueue(4 KiB) -> writer thread -> pty
    [exit] waitpid via per-session TermSessionWaiter thread -> MSG_PROCESS_EXITED -> cleanupResources
          -> "[Process completed...]" printed -> onSessionFinished; --kill-on-exit reaps guest tree""",
 "Sequence: session creation through proot exec and PTY teardown. Every element is code-cited in chapters 4-7."),
("h2", "3.2 The two launch profiles (exact argv)"),
("p", lbl("OBS") + " Both profiles share the same builder, proot binary, rootfs, and apk cache; they differ "
      "only in binds, and the difference is derived, require-guarded, and test-pinned (" +
      cite("RuntimeProcessLauncher.kt:284-404") + "). Long options are pinned to the " + mono("--opt=value") +
      " joined form because proot v5.1.107.92 accepts only that shape."),
("table",
 ["Element", "INTERACTIVE_TERMINAL (every user session)", "PACKAGE_OPERATION (app-side apk execs)"],
 [
  ["executable", "<nativeLibraryDir>/libproot.so", "same"],
  ["common flags", "--kill-on-exit --link2symlink --rootfs=<rootfsDir> --root-id --cwd=/root", "same"],
  ["/dev", "--bind=/dev  (host devtmpfs; brings /dev/ptmx + /dev/pts)", "same"],
  ["/proc", "--bind=/proc  (ALWAYS; host procfs, hidepid=2)", "NEVER (derived + require-guard)"],
  ["sysdata", "+ 5 file-over-file binds right after /proc: /proc/{stat,uptime,loadavg,version,vmstat}", "none (guard: overlays require interactive + /proc)"],
  ["/sys", "--bind=/sys  (host sysfs; some nodes deny reads - honest)", "same"],
  ["apk cache", "--bind=<apkCache>/etc:/etc/apk/cache --bind=<apkCache>/var:/var/cache/apk", "same"],
  ["guest command", "/bin/sh -l  (or /bin/sh -l -c '<cmd>; exec /bin/sh -l' for command apps)", "/sbin/apk update|search|add|del|info ..."],
  ["env", "LD_LIBRARY_PATH=<nativeLibraryDir>, PROOT_LOADER=<loader>, PROOT_TMP_DIR=<cache>/proot-tmp, HOME=/root, PATH=/usr/local/sbin:...:/bin, TERM=xterm-256color, LANG=C.UTF-8, TMPDIR=/tmp, PROOT_LOADER_32=<loader32 if shipped>", "same"],
  ["cwd (host)", "filesDir/home", "filesDir/home"],
  ["PTY", "yes (allocated by termux JNI on first view layout)", "no - headless via GuestCommandRunner (stdin /dev/null, daemon pipe drains)"],
 ],
 [0.14, 0.46, 0.40]),
("h2", "3.3 Lifecycle, ownership, cleanup"),
("p", lbl("OBS") + " Ownership is strictly hierarchical. The process-scoped " + mono("TerminalSessionManager") +
      " object owns every session entry; each vendored " + mono("TerminalSession") + " owns its PTY master fd, "
      "two ByteQueues, three threads (reader, writer, waiter), and its emulator. The foreground service "
      "(" + mono("foregroundServiceType=specialUse") + ") is started when the first session exists and calls "
      + mono("stopSelf()") + " when the last one finishes or is closed - its only job is keeping the app "
      "process (and therefore the child process tree) alive while backgrounded. Cleanup is airtight: "
      + mono("--kill-on-exit") + " makes proot kill its whole guest tree on exit; every spawned process is "
      "reaped by its dedicated waiter thread (waitpid), so no zombies are possible; " +
      mono("finishIfRunning()") + " escalates to SIGKILL when the app closes a tab. Process death (LMK) "
      "destroys everything - there is deliberately no fake session restore; the notification and docs say "
      "so. " + lbl("INF") + " This is the same honesty model as Termux and it is the correct one for a "
      "side-loaded terminal product."),

# ============================ CHAPTER 4 ============================
("h1", "4. Termux-Derived Components (Terminal / PTY / Keyboard)"),
("h2", "4.1 What was inherited, byte-for-byte"),
("p", lbl("OBS") + " The two vendored modules are unmodified upstream Termux sources at " +
      mono("3b66f879") + " (verified: the git history records a single byte-identical vendoring commit " +
      mono("89ee4e0") + " and the THIRD_PARTY.md modification table lists build-script changes only). "
      "Upstream packages " + mono("com.termux.terminal") + " and " + mono("com.termux.view") + " are kept "
      "verbatim so future re-syncs remain mechanical diffs. The 19 upstream JVM test classes are vendored "
      "unchanged and run as part of the 752-test suite. The JNI layer (" + mono("termux.c") + ") provides "
      "exactly " + mono("createSubprocess") + ", " + mono("setPtyWindowSize") + ", " +
      mono("setPtyUTF8Mode") + ", " + mono("waitFor") + ", " + mono("close") + " - no PocketShell additions."),
("h2", "4.2 Per-session resource anatomy"),
("table",
 ["Resource", "Value", "Notes"],
 [
  ["PTY master fd", "1", "opened on /dev/ptmx host-side; guest PTYs come from the same devpts through the /dev bind"],
  ["Java threads", "3 (TermSessionInputReader, TermSessionOutputWriter, TermSessionWaiter)", "one set per session, process-scoped lifetime"],
  ["ByteQueues", "64 KiB (process->terminal) + 4 KiB (terminal->process)", "write() blocks when full -> natural backpressure into the PTY"],
  ["Main-handler buffer", "64 KiB receive buffer", "TerminalSession.java:339"],
  ["Emulator buffer", "TerminalBuffer, 2000 total rows (transcript) + alt screen", "ShellEnvironment.TRANSCRIPT_ROWS=2000; upstream bounds 100..50000"],
  ["Row storage", "char[1.5*cols] (3 B/cell) + long[cols] styles (8 B/cell) = ~11 B/cell", "allocated lazily per row"],
  ["Est. heap per session", "~1.0 MB @40 cols, ~1.5 MB @60, ~1.9 MB @80 (full scrollback)", "+ ~160 KiB fixed I/O overhead; fresh session ~30-200 KiB"],
 ],
 [0.22, 0.40, 0.38]),
("h2", "4.3 Input path verification (Ctrl+C, Ctrl+Z, Ctrl+D, ESC, arrows)"),
("p", lbl("OBS") + " The byte path is verified end-to-end: deck key -> " + mono("TerminalKeyDispatcher") +
      " (chars via " + mono("KeyCharacterMap.VIRTUAL_KEYBOARD.getEvents()") + "; specials via synthetic "
      "KeyEvents with metaState=0) -> " + mono("KeyboardInputRouter.resolve()") + " -> " +
      mono("TerminalView.onKeyDown") + " -> " + mono("KeyHandler.getCode") + " (UPSTREAM, UNMODIFIED) -> " +
      mono("session.write") + " -> 4 KiB queue -> writer thread -> PTY. Ctrl+letters flow through " +
      mono("onCodePoint(codePoint, controlDown, session)") + " where PocketShell maps a-z to 0x01-0x1A. "
      "Therefore: Ctrl+C sends 0x03, Ctrl+Z 0x1A, Ctrl+D 0x04, Ctrl+L 0x0C, Ctrl+A 0x01, Ctrl+E 0x05, "
      "Ctrl+W 0x17; ESC sends 0x1B via KeyHandler; arrows send CSI/SS3 sequences honoring application "
      "cursor mode; TAB 0x09; F1-F4 SS3 P-S, F5-F12 CSI 15..24~. The kernel line discipline and the guest "
      "shell receive standard bytes - no runtime change in chapters 5-7 can alter this path, because it "
      "terminates at the PTY before proot is involved."),
("callout", "FINDING (correctness).", "PocketShellTerminalViewClient.onCodePoint always returns true, so the "
            "upstream ctrl transliteration block in TerminalView.java:863-885 is dead code in this app. The "
            "replacement sends literal characters for Ctrl+3..8, Ctrl+[ / backslash / ], Ctrl+_, Ctrl+/ "
            "(upstream: control bytes), maps '^' to 0 instead of 30, and drops altDown so Alt+letter loses "
            "its ESC prefix on the char path (readline M-b/M-f via hardware keyboards). KeyHandler.java itself "
            "is unmodified. Small fix: return false for unmapped combos. Additionally the blinker keeps "
            "invalidating at blink rate when the app hides the cursor (onTerminalCursorStateChange ignored)."),
("h2", "4.4 Rendering and M5.1 F3"),
("p", lbl("OBS") + " Rendering is upstream " + mono("TerminalRenderer") + " bitmap-row blitting into a Canvas "
      "inside an " + mono("AndroidView") + " - zero Compose recomposition per output frame; recomposition is "
      "driven only by low-frequency state (session list, tab selection, throttled titles, modifier flags). "
      "M5.1 F3 (" + cite("TerminalScreen.kt:136-153") + ") gates " + mono("TerminalView.onScreenUpdated()") +
      " to the visible session id via " + mono("rememberUpdatedState") + ", so a background session streaming "
      "output no longer forces repaints of the unchanged visible screen. " + lbl("INF") + " Hidden sessions "
      "still PARSE output on the main thread (emulator append is unconditional); only the repaint is skipped. "
      "That residual cost is the main remaining terminal-side optimization candidate (chapter 13). Resize "
      "uses " + mono("TIOCSWINSZ") + " + SIGWINCH (kernel-delivered); column-change resizes rebuild up to "
      "2000 TerminalRow objects on the main thread (upstream reflow), visible during pinch-zoom."),
("h2", "4.5 Keyboard"),
("p", lbl("OBS") + " The deck is pure Compose (~50-70 key composables, per-key press state, 80-100 ms "
      "animated press feedback, haptics, auto-repeat 350/60 ms, long-press F1-F10 on the number row). "
      "Modifiers are a state machine (OFF -> ONE_SHOT -> LOCKED) surfaced through peek hooks (" +
      mono("readControlKey/readAltKey/readShiftKey") + ") and cleared on session switch. The system IME is "
      "hard-blocked app-wide (FLAG_ALT_FOCUSABLE_IM + hidden ime() insets). " + lbl("INF") + " Per-press "
      "allocation is trivial (coroutine Job + possible popup); M5.1 touched no keyboard file and found "
      "nothing to fix. Do not redesign; the only watch item is total recomposition when the SYMBOL page "
      "swaps, which is infrequent."),

# ============================ CHAPTER 5 ============================
("h1", "5. PRoot Deep Audit"),
("h2", "5.1 Identity, build, and patches"),
("p", lbl("OBS") + " PocketShell depends on the Termux PRoot fork: " + mono("termux/proot") + " tag " +
      mono("v5.1.107.92") + " @ " + mono("7266fb3e8516535682f5a9c8f3a7e70f6506eddb") + " (proot 5.1.0 plus "
      "Termux's Android patches), GPL-2.0, self-compiled with NDK r28c against API 26 for all four ABIs " +
      mono("(scripts/build_proot_m23.sh)") + ". libtalloc 2.4.2 is linked dynamically (SONAME normalized). "
      "Two documented build-time micro-patches exist (a " + mono("string.h") + " include for bionic + clang 16 "
      "strictness, and a mawk-compatible " + mono("loader-info.awk") + "). The split-loader scheme uses "
      "PROOT_LOADER to point at the shipped " + mono("libproot-loader.so") + " (and PROOT_LOADER_32 on "
      "arm64/x86_64 for 32-bit guest binaries), keeping the embedded loader as fallback. Termux's " +
      mono("libandroid-shmem") + " is intentionally absent: minSdk 26 has native SysV shm in bionic."),
("h2", "5.2 Mechanism and per-area behavior"),
("table",
 ["Area", "Behavior under this proot build", "Consequence for PocketShell"],
 [
  ["Syscall interception", "ptrace-based (PTRACE_TRACEME + syscall stops); path translation to/from rootfs; uid/gid lies via --root-id; NO kernel namespaces, NO root privileges", "No real mounts anywhere; every 'mount' is a per-process bind recorded at exec; guest dies with session (--kill-on-exit)"],
  ["mount(2)", "Emulated: recorded as a bind for that process tree only", "Manual 'mount -t proc' inside a session 'works' but is per-session and never a fix (the m3.6 diagnosis evidence)"],
  ["exec of guest binaries", "Child execve translated to the rootfs path; file lives in app_data_file, executable ONLY because targetSdk 28 maps to untrusted_app_27 (chapter 6)", "Entire guest depends on the targetSdk-28 domain exemption"],
  ["link()/linkat()", "--link2symlink intercepts at ptrace layer, emulates hard links as symlink chains with link-count translation", "Makes apk package extraction (binutils/gcc/g++ tar hardlinks) and O_TMPFILE-era workflows possible at all; honest cost: links become symlinks, disk counts each copy; visible .l2s anchors can EPERM a guest link() on them (uv/Hermes incident, UV_LINK_MODE=copy workaround)"],
  ["/proc", "Host procfs bind, translated; file-over-file binds layered on top (more specific path wins)", "See chapter 7 for the full analysis"],
  ["Signals", "No interference with PTY-driven signals (SIGWINCH from TIOCSWINSZ, SIGINT from 0x03, SIGTSTP from 0x1A); ptrace signal delivery is transparent for the interactive path", "Ctrl+C/Z/D reliability is upstream-terminal + kernel, not proot"],
  ["mmap / shm", "Normal mmaps pass through; sysvipc extension compiles against bionic directly", "No shims needed at minSdk 26"],
  ["Performance", "Every traced syscall pays stop/translate/continue; exec passes through the loader; per-path-lookup translation cost", "Syscall-heavy workloads (compiles, git status on big trees, node startup) are measurably slower than native; bounded and acceptable - measure, do not rewrite (chapter 13)"],
 ],
 [0.14, 0.44, 0.42]),
("h2", "5.3 Which limitations come from PRoot, and the improvement options"),
("p", lbl("INF") + " Attributable to PRoot itself: ptrace syscall overhead; absence of a PID namespace "
      "(guest PIDs are host-global, process visibility shaped by hidepid instead); per-session bind "
      "semantics (no persistent environment mounts); the link2symlink second-order artifact (.l2s anchors). "
      "NOT attributable: SELinux exec/link denials (Android), glibc-vs-musl binary failures (guest libc), "
      "hidepid (kernel mount option set by Android)."),
("p", lbl("REC") + " Improvement ladder (audit conclusion only - nothing implemented): (1) keep the pinned "
      "fork and track Termux upstream - the fork already carries the two extensions that matter "
      "(link2symlink, split loader); (2) newer proot releases could be adopted via the existing build script "
      "when they matter (watch: proot 5.2/6.x tracemap and memory work); (3) configuration-level wins are "
      "exhausted (the argv shape is test-pinned and minimal); (4) replacing the loader or selected ptrace "
      "behavior means forking for real - only justified by a measured, specific bottleneck; (5) a full "
      "native runtime layer (own tracer or container) is the last resort and is NOT recommended now: "
      "mature open-source already solves every current need (Phase Q principle)."),

# ============================ CHAPTER 6 ============================
("h1", "6. Android Restrictions and Execution Model"),
("h2", "6.1 The load-bearing fact: targetSdk 28"),
("p", lbl("OBS") + " AOSP " + mono("system/sepolicy/private/app_neverallows.te") + " (verified in "
      "docs/M2-RESEARCH.md section 1.1, fetched 2026-09-01):"),
("code",
"""neverallow { all_untrusted_apps
             -untrusted_app_25
             -untrusted_app_27
             -runas_app
           } { app_data_file privapp_data_file }:file execute_no_trans;

all_untrusted_apps = { untrusted_app(_29|_30|_32|...), ... }
seapp_contexts: targetSdk 28 -> untrusted_app_27   |   targetSdk 29 -> untrusted_app_29""",
 "The exemption list is the whole story: domains for targetSdk <= 28 may exec_no_trans app_data_file; "
 "domains for targetSdk >= 29 may not."),
("p", lbl("OBS") + " Therefore any app with targetSdk >= 29 can never execve() a file from its own writable "
      "data - which would make a proot guest impossible, because the rootfs, the guest shell, and every apk-"
      "installed binary live in app-private storage. PocketShell pins targetSdk 28 for exactly this reason, "
      "documented at " + cite("app/build.gradle.kts:16-24") + " and identical to Termux's famous refusal to "
      "bump. Two device incidents prove the mechanism empirically: v0.3.0 (extractNativeLibs=false left "
      "nativeLibraryDir empty -> proot 'missing' crash) and v0.3.1 (the targetSdk 36 -> 28 move with "
      + mono("useLegacyPackaging = true") + "). " + lbl("INF") + " Side-load distribution makes the Play "
      "targetSdk floor irrelevant; Android 14+ still installs targetSdk >= 23. This also had an unexpected "
      "UI consequence: algorithmic Force Dark defaults on for legacy-target apps fed the Companion "
      "black-canvas saga (m4.0.5-m4.0.8)."),
("h2", "6.2 Where everything executable lives"),
("table",
 ["Artifact", "Location", "Why it can execute"],
 [
  ["libproot.so (the guest entry exec)", "nativeLibraryDir (from jniLibs, extracted at install)", "App lib dir is executable for all domains; useLegacyPackaging keeps path-based execve possible"],
  ["libproot-loader.so / loader32", "nativeLibraryDir", "Loaded via PROOT_LOADER env (proot re-exec's its loader); same exec rules"],
  ["libtermux.so (PTY JNI)", "nativeLibraryDir", "Standard System.loadLibrary"],
  ["Guest shell + apk-installed binaries", "noBackupFilesDir/runtime/rootfs/... (app_data_file)", "Executable ONLY in the untrusted_app_27 domain (targetSdk 28)"],
  ["Patched libapk asset", "assets/guest/... -> copied into rootfs by GuestApkCompat", "Data file, loaded (not exec'd) as a library by the guest"],
 ],
 [0.28, 0.30, 0.42]),
("h2", "6.3 Execution capability matrix (Phase E questions)"),
("table",
 ["Binary class", "Current status", "Mechanism / restriction", "Possible solution if blocked"],
 [
  ["musl ARM64 dynamic", "WORKS (verified end-to-end on device)", "Guest ld-musl-aarch64.so.1 inside rootfs; exec via app_data exemption", "n/a"],
  ["glibc ARM64 dynamic", "FAILS TODAY", "No /lib/ld-linux-aarch64.so.1, no glibc libs in the musl rootfs; gcompat proven insufficient for sanitizer-embedded or glibc-private-symbol binaries (Antigravity SIGSEGV)", "glibc sidecar prefix + own loader (chapters 8, 17)"],
  ["Static ARM64", "WORKS", "No interpreter needed; pure kernel exec after proot path translation", "n/a"],
  ["Downloaded executables (by user, in guest)", "WORKS if musl-compatible + exec bit", "Same app_data exec rules; SELinux neverallows link() not exec; chmod +x survives on app_data", "n/a (product already relies on this for CLI agents)"],
  ["apk-installed binaries", "WORKS", "apk add through the no-/proc commit path + link2symlink extraction", "n/a"],
  ["User-compiled in guest", "WORKS for musl targets", "apk gcc/clang/binutils extract via link2symlink (device-proven after the v0.6.1 hardlink incident)", "glibc-targeting builds need the sidecar (ld, headers, libstdc++)"],
  ["Binaries in app-private storage (host side)", "n/a - guest-only execution", "Host-side app data is exec-capable only in-domain (same rule as guest paths)", "n/a"],
  ["Binaries on shared storage", "DOES NOT WORK (and should not)", "No storage permission declared; FUSE mounts are noexec for app data; SELinux blocks exec on external storage for untrusted apps", "Copy into the guest first (the only honest path)"],
 ],
 [0.20, 0.20, 0.36, 0.24]),
("p", lbl("OBS") + " The shipped " + mono("libproot-loader32.so") + " (arm64 + x86_64 builds, 6,120 B) is "
      "proot's 32-bit loader hook, exposed via PROOT_LOADER_32 - the platform keeps the door open for "
      "32-bit guest binaries without shipping a 32-bit rootfs today."),
]

# ============================ CHAPTER 7 ============================
BLOCKS += [
("h1", "7. /proc Architecture Analysis"),
("h2", "7.1 How /proc is provided today"),
("p", lbl("OBS") + " /proc is the REAL host procfs, bind-mounted by proot (" + mono("--bind=/proc") + ") "
      "into the guest for every interactive session since v0.7.0-m3.6 - unconditionally, with no remaining "
      "code path that can express a no-/proc user session (" + cite("RuntimeProcessLauncher.kt:295-360") +
      " and the fail-loud " + mono("procContractProblem()") + " audit). Because Android mounts proc with "
      "hidepid=2, the guest sees exactly one process tree: the app's own, with host PIDs - honestly, not "
      "spoofed. On top of the real /proc, five kernel-denied standard files are repaired by probe-first "
      "file-over-file binds: /proc/stat, /proc/uptime, /proc/loadavg, /proc/version, /proc/vmstat (" +
      cite("GuestSysDataCompat.kt:85-91") + "). Probe-first means a real readable file is NEVER overlaid; "
      "overlay contents are honest syntheses (uname-derived version with an attribution marker, real "
      "elapsedRealtime uptime, real btime in stat, zero-jiffy placeholders), written with "
      "CREATE_NEW+NOFOLLOW hardening and re-verified from disk. Package operations never bind /proc - a "
      "require() guard makes that state inexpressible (" + cite("RuntimeProcessLauncher.kt:312-315") + ")."),
("h2", "7.2 The exact denial trace (why some files read and others EACCES)"),
("p", "When the guest reads a /proc path, the following chain executes. " + lbl("OBS") + " Steps 1-3 are "
      "proot; steps 4-6 are kernel/SELinux; the outcome differs per file type:"),
("code",
"""guest open("/proc/version")
  1. ptrace stop: proot sees the syscall, translates path via its bind table
  2. file-over-file bind match? YES (version is overlaid)
       -> rewrite to <app noBackupFilesDir>/runtime/sysdata/version  (app_data_file, readable)
       -> kernel open SUCCEEDS  -> guest gets the synthesized, attribution-marked content
     NO match (e.g. /proc/self/status, /proc/meminfo, /proc/cpuinfo):
       -> rewrite to real host /proc/...
  3. proot resumes the syscall on the host path
  4. kernel DAC: app uid owns nothing under /proc -> relies on procfs world-read bits
  5. SELinux: untrusted_app_27 domain vs the target's proc_type:
       proc_type { proc proc_net proc_sysctl ... } readable subsets -> SUCCESS (meminfo, cpuinfo, self/*)
       proc_version (One UI policy at targetSdk 28), kcore, kmsg, sysrq-trigger, most /proc/<other-pid>:
       -> EACCES  ("Permission denied" - the honest wall the guest sees)
  6. hidepid=2 (mount option set by Android): other-uid /proc/<pid> dirs are not even visible""",
 "Trace of a /proc read: proot translation, then real kernel+SELinux evaluation. The wall is Android's; the "
 "overlay is PocketShell's honest repair."),
("h2", "7.3 Policy history (removed/added binds over development)"),
("table",
 ["Version / date", "/proc decision", "Why"],
 [
  ["M2.3 (v0.3.0, 2026-09-01)", "--bind=/proc for the guest", "Full rehearsal shape; apk not yet wired, no tension"],
  ["v0.4.2 (2026-09-01)", "PACKAGE specs DROP /proc", "apk-tools O_TMPFILE+linkat('/proc/self/fd/N') commit hits AOSP neverallow all_untrusted_apps ... link; without /proc apk uses named-tmpfile+renameat (allowed). Device: 'updating and opening ... Permission denied'"],
  ["v0.5.0 (2026-09-02)", "Interactive sessions ALSO drop /proc", "Same SELinux linkat denial hit manual in-guest apk; cost: ps/top/htop empty (accepted, documented)"],
  ["M2.6 (v0.6.0, 2026-09-02)", "Conditional: /proc only when guest libapk verified byte-patched", "One-byte '/proc/self/fd' -> '/proc/self/fX' gate patch (checksum-pinned) keeps apk on the renameat path while /proc returns"],
  ["v0.6.2 (2026-09-02)", "+ sysdata overlays (probe-first, 5 files)", "Adapted from proot-distro sysdata.py; real-wins rule; repairs version/stat/uptime/loadavg/vmstat walls"],
  ["v0.7.0-m3.6 (2026-09-04)", "UNCONDITIONAL /proc for interactive + pattern-based self-repair + fail-loud spawn audit", "In-guest apk upgrade replaced the patched lib -> checksum pin missed -> silent no-/proc sessions -> Kilo Code (Bun) died ENOENT realpath on EXISTING dirs: aarch64 has NO realpath syscall; Bun resolves via /proc/self/fd readlink; coreutils never noticed. Lesson: pin byte patterns, not whole-file checksums, on files the guest may replace"],
 ],
 [0.20, 0.34, 0.46]),
("h2", "7.4 Classification of /proc restrictions (the five questions)"),
("table",
 ["Question", "Answer"],
 [
  ["1. Caused by Android (kernel/SELinux/hidepid)?", "hidepid=2 process isolation; EACCES on kernel-internal nodes (kcore, kmsg, sysrq, most /proc/sys writes); proc_version denial (One UI); denial of link()/linkat() operations (indirectly shaped the whole /proc policy)"],
  ["2. Caused by PRoot?", "No PID namespace - guest PIDs are host-global and scoped by hidepid instead of a namespace; mount(2) is per-session emulation, so a guest mount never persists; translation quirks (e.g. reading through /proc/self/fd paths interacts with link2symlink anchors)"],
  ["3. Caused by the guest (Alpine/musl)?", "musl tool expectations (ps/top from busybox work fine once /proc exists); Bun/node realpath-via-procfs behavior is a guest-runtime design that REQUIRES procfs - a guest-side dependency on an Android-filtered resource"],
  ["4. Improvable by our runtime?", "More sysdata overlays for other standard files Android denies (carefully, probe-first); richer synthesized content (per-CPU stats are already real-core-count); /proc/sys read-only emulations if a tool needs them. The self-repair mechanism is already the model for version-independent guest drift"],
  ["5. Fundamentally unavailable without root/system privileges?", "Cross-uid process visibility (hidepid is kernel-enforced), /proc/kcore, /proc/kmsg, /proc/sysrq-trigger, writable /proc/sys kernel tunables, a real per-guest PID/NET namespace view, /proc/<pid> of other apps. No userspace runtime can conjure these without a kernel-level boundary (root or a real container/VM)"],
 ],
 [0.26, 0.74]),

# ============================ CHAPTER 8 ============================
("h1", "8. ELF/libc Analysis and Compatibility Strategy"),
("h2", "8.1 The current guest"),
("p", lbl("OBS") + " The guest is Alpine 3.24.1 userspace on musl (musl libc, busybox core, apk-tools 3). "
      "Dynamic linker: " + mono("/lib/ld-musl-aarch64.so.1") + ". The rehearsal battery (docs/"
      "ANTIGRAVITY-PLATFORM.md section 6) proves the musl ecosystem works on device: node 24.18.1 (apk "
      "nodejs-current), npm 11.12.1, Python 3.12.14, git 2.54.0, curl HTTP/2, OpenSSH 10.3p1, htop 3.5.3. "
      "The failure class is equally recorded: any binary linked against glibc - or embedding a glibc-built "
      "sanitizer runtime - does not run, and gcompat does not save it."),
("h2", "8.2 The gcompat evidence (why option B is a dead end)"),
("p", lbl("OBS") + " The Antigravity CLI ladder (2026-09-04, qemu-aarch64 rehearsal): its glibc aarch64 "
      "binary runs correctly on real glibc (Debian 2.44, exit 0); stock Alpine gcompat fails on missing "
      "glibc-private symbols (" + mono("__read/__open/__lseek/pvalloc") + "); gcompat + a four-symbol shim "
      "starts, then SIGSEGVs inside the binary's embedded sanitizer runtime. " + lbl("INF") + " This is not "
      "one tool's bug - it is the class boundary: gcompat implements a subset of glibc's ABI surface and "
      "fundamentally cannot cover private symbols, sanitizer/malloc interposition, NSS dlopen patterns, or "
      "jemalloc-style allocator hooks. Any strategy built on gcompat will accumulate one-off shims that "
      "break per tool and per version. Do not ship gcompat as the compatibility story."),
("h2", "8.3 Ecosystem reality (how much needs glibc?)"),
("p", lbl("INF") + " Categorized by distribution channel, the modern ARM64 developer-CLI world splits as "
      "follows. (1) apk/Alpine-packaged tools (busybox world, git, python3, nodejs-current, neovim, tmux, "
      "openssh, go, rust, gcc/clang): musl-native, fully covered today. (2) Vendor-first-party static or "
      "musl builds (Go binaries with CGO_DISABLED, some Rust tools, some node 'musl' tarballs): covered "
      "today when they exist - but upstream availability is inconsistent and unverifiable without network "
      "probing per release. (3) Vendor first-party glibc builds - the DEFAULT channel for most commercial "
      "CLIs: official Node.js tarballs, npm-installed agent stacks (Kilo/Claude Code/Codex/OpenCode-class "
      "tools are npm or Bun distributions), anything shipping prebuilt native wheels or an embedded "
      "sanitizer. This third category is where PocketShell is blocked today, and it is exactly the "
      "category the product's 'Your tools' model targets. " + lbl("UNK") + " A precise percentage is not "
      "provable from the repo alone; the honest statement is: the majority of first-party vendor ARM64 "
      "CLI distributions assume glibc, and the minority that publish musl builds do so inconsistently."),
("h2", "8.4 Options A-E (Phase F decision matrix)"),
("table",
 ["Option", "What it is", "Size (ARM64)", "Verdict"],
 [
  ["A. musl only (status quo)", " apk + source builds only", "0 MB", "Insufficient: blocks the primary commercial CLI ecosystem; every new tool becomes a porting project"],
  ["B. musl + gcompat", "glibc->musl ABI shim", "~1-2 MB", "REJECT as primary: proven inadequate (private symbols, sanitizers); produces per-tool shim debt"],
  ["C. Minimal glibc sidecar runtime", "PocketShell-owned prefix with real glibc: ld-linux-aarch64.so.1 + libc/libm/libpthread/libdl/librt/resolv + libstdc++ + libgcc, provisioned like the rootfs (pinned download, sha256)", "~15-25 MB compressed / 50-80 MB installed (libs-only); a full Debian minbase would be 40-60 MB compressed / 150-200 MB installed - estimate, not measured", "RECOMMEND: the platform lever. Upstream binaries run against real glibc; coexists with musl in one guest"],
  ["D. Statically linked PocketShell-native builds of selected tools", "Rebuild each strategic tool against musl, ship pinned", "5-40 MB per tool", "Only for 1-2 strategic anchors we control end-to-end; per-tool maintenance + license review burden scales badly"],
  ["E. Combination (C + apk + selective D)", "Sidecar platform + musl apk ecosystem + static anchor tools where justified", "C's size + per-tool", "RECOMMEND as the long-term shape; start with C alone"],
 ],
 [0.16, 0.34, 0.20, 0.30]),
("h2", "8.5 Recommended design: one guest, two libcs, invisible selection"),
("p", lbl("REC") + " Keep the Alpine guest as the only user-visible environment. Add a PocketShell-owned "
      "glibc prefix at a fixed guest path - the natural shape is " + mono("/pocketshell/runtime/glibc-aarch64/") +
      " - containing its own loader and libraries, provisioned by the SAME trust pipeline as the rootfs "
      "(pinned URL, size + sha256, staged extraction, atomic promote). Then make launcher selection a "
      "guest-side detail:"),
("code",
"""Install-time (per glibc package/bundle):
  /usr/local/bin/cline                      # launcher shim (user-visible path unchanged)
    -> reads PT_INTERP of /opt/cline/bin/cline.real   # or matches a bundled manifest
    -> execs /pocketshell/runtime/glibc-aarch64/lib/ld-linux-aarch64.so.1 \\
         --library-path /pocketshell/runtime/glibc-aarch64/lib:/opt/cline/lib \\
         /opt/cline/bin/cline.real "$@"
Result:
  /usr/bin/tool      -> musl (apk)          # unchanged behavior
  /usr/local/bin/cline -> glibc via sidecar # works, user never sees the machinery""",
 "Launcher-selection pattern: the ELF interpreter (or a manifest) chooses the loader; the user sees one PATH."),
("p", lbl("INF") + " This coexists cleanly with Alpine/musl because the two libcs never mix in one process: "
      "the sidecar's " + mono("--library-path") + " precedes the musl system paths, and the glibc loader "
      "loads its own dependencies. Known sharps to engineer around (documented now, solved in "
      "implementation): /etc inside the sidecar (nsswitch, resolv.conf -> reuse the PocketShell-managed "
      "resolv.conf), locale/archive presence, and any tool that hardcodes " + mono("/lib64/ld-linux") +
      " paths (solvable with a two-line wrapper or proot bind of the sidecar's loader at that path for "
      "that process). A second full distro rootfs (Debian/Ubuntu guest alongside Alpine, as floated in "
      "ANTIGRAVITY-PLATFORM.md section 5) remains the escalation path if the sidecar proves insufficient - "
      "it reuses the same proot, launcher, and trust pipeline, at a larger size and with a session-model "
      "decision (which distro is 'the' shell)."),

# ============================ CHAPTER 9 ============================
("h1", "9. Developer-Tool Compatibility Matrix"),
("p", "Conceptual matrix per Phase O - no installs were performed during this audit. Statuses marked "
      "device-verified come from the rehearsal battery and recorded sessions; others are architecture-based "
      "expectations. " + lbl("UNK") + " One correction to the audit brief: no tool named 'Cline' appears "
      "anywhere in the repository record; the documented incidents are the Antigravity CLI (musl 404 -> "
      "gcompat SIGSEGV) and Kilo Code (Bun/aarch64 realpath vs missing /proc). 'Cline' is assessed below "
      "by class (npm-distributed Node CLI)."),
("table",
 ["Tool", "Runtime needed", "libc", "ARM64 build", "Compatibility today", "After Runtime 2.0 (glibc sidecar)", "Required PocketShell support"],
 [
  ["Node.js", "self", "musl via apk nodejs-current (v24.18.1 device-verified); official tarballs are glibc", "yes", "apk node: WORKS; official glibc tarball: FAILS", "both work", "none beyond sidecar"],
  ["Python", "self", "musl (apk)", "yes", "WORKS (3.12.14 device-verified)", "unchanged", "none"],
  ["Git", "self", "musl (apk)", "yes", "WORKS (2.54.0 verified)", "unchanged", "link2symlink already handles pack hardlinks"],
  ["Hermes", "python/uv launcher", "musl guest env", "yes", "WORKS after profile PATH fix (uv link-mode incident documented; UV_LINK_MODE=copy)", "unchanged", "keep .l2s caveat documented"],
  ["Kilo Code", "Bun-embedded", "musl-compatible Bun + /proc requirement", "yes", "WORKS since m3.6 (realpath needs /proc/self/fd on aarch64)", "unchanged", "procfs contract (done)"],
  ["Cline (npm-class agent)", "Node >= 18", "glibc (npm native deps) expected", "yes", "EXPECTED FAIL today (glibc deps); no on-device record", "EXPECTED WORK via sidecar Node", "sidecar + launcher shim"],
  ["OpenCode", "Node/Bun", "per distribution", "yes", "registry-seeded; probe surfaces if musl-compatible", "works if glibc-linked", "sidecar"],
  ["Claude Code", "Node >= 18 (npm)", "glibc (official Node)", "yes", "EXPECTED FAIL today", "EXPECTED WORK via sidecar Node", "sidecar + launcher shim"],
  ["Codex", "Node (npm)", "glibc expected", "yes", "same class as Claude Code", "same", "same"],
  ["Neovim", "self", "musl (apk)", "yes", "WORKS (apk package class)", "unchanged", "none"],
  ["tmux", "self", "musl (apk)", "yes", "WORKS expected - needs /dev/ptmx (rides the /dev bind) and /proc for pane info", "unchanged", "none (already provided)"],
  ["SSH (client)", "self", "musl (apk)", "yes", "WORKS (OpenSSH 10.3p1 verified)", "unchanged", "none; agent sockets live inside guest fs"],
 ],
 [0.13, 0.13, 0.20, 0.08, 0.22, 0.13, 0.11]),
("p", lbl("INF") + " The pattern is crisp: everything the apk ecosystem can build runs today; everything "
      "distributed as a first-party glibc binary is blocked exclusively by libc, not by proot, exec policy, "
      "or storage. That is why the compatibility investment should go entirely into chapter 8's sidecar, "
      "not into runtime workarounds."),
]

# ============================ CHAPTER 10 ============================
BLOCKS += [
("h1", "10. Storage and Filesystem Analysis"),
("h2", "10.1 Current layout (complete)"),
("table",
 ["Host path", "Contents", "Notes"],
 [
  ["noBackupFilesDir/runtime/rootfs/", "Extracted Alpine rootfs (promoted by atomic rename)", "durable, excluded from auto-backup; wiped on uninstall"],
  ["noBackupFilesDir/runtime/runtime.json", "Install metadata (written LAST in staging; sha256 re-asserted after promote)", "structural READY signal"],
  ["noBackupFilesDir/runtime-download.tmp / runtime-extract.tmp/", "In-flight download / staging extraction", "same volume as runtime/ so promotion is a single rename; orphans cleaned at startup reconcile"],
  ["noBackupFilesDir/apk-cache/{etc,var}/", "App-owned apk cache bound over /etc/apk/cache + /var/cache/apk", "cache lives OUTSIDE the rootfs so rootfs permissions can never block apk"],
  ["noBackupFilesDir/runtime/sysdata/", "Source files for the 5 /proc overlays", "rootfs sibling (proot-distro upstream layout convention)"],
  ["filesDir/home/", "Host-side session cwd (proot cwd before --cwd=/root takes effect in-guest)", "per-session env/cwd captured at creation"],
  ["cacheDir/tmp, cacheDir/proot-tmp/", "M1 shell TMPDIR; PROOT_TMP_DIR", "disposable by nature"],
  ["nativeLibraryDir/", "libproot.so, libproot-loader.so(32), libtalloc.so, libtermux.so", "the ONLY exec-capable location for the app's own binaries"],
 ],
 [0.30, 0.38, 0.32]),
("p", lbl("OBS") + " No external storage is touched anywhere (repo-wide grep for externalCacheDir/"
      "getExternalFilesDir: zero hits). No quotas or cleanup policies exist; the only space feature is "
      "honest reporting (RuntimeDiagnostics: recursive runtime size, StatFs free space)."),
("h2", "10.2 Failure-mode analysis"),
("table",
 ["Failure class", "Observed in history?", "Current mitigation", "Residual risk"],
 [
  ["link() failures", "YES - apk package extraction (binutils/gcc/g++), apk O_TMPFILE commit, uv/Hermes .l2s", "link2symlink for extraction; no-/proc commit path for apk; pattern self-repair for in-guest apk", ".l2s anchor EPERM class remains for tools that link() over proot-generated anchors (documented workaround UV_LINK_MODE=copy)"],
  ["rename() failures", "NO", "Same-volume staging -> promote rename; EXDEV structurally impossible within one volume", "none identified"],
  ["execute failures", "YES - v0.3.0 (empty nativeLibraryDir), v0.3.1 (linker path)", "useLegacyPackaging; LD_LIBRARY_PATH in spec; argv[0]-is-path convention; preflightProblem() honest gate", "targetSdk bump would break everything (F8 guard)"],
  ["permission problems", "YES - resolv.conf zone-suffix rejections, cache perms", "combined DNS file, app-owned cache binds, ensureApkWorkspace modes (tmp 1777)", "user-modified guest files are never touched (honesty rule) - by design"],
  ["slow I/O", "not observed as an incident", "64 KiB reader buffer, bounded scrollback, lazily-allocated rows", "rootfs on f2fs/ext4 internal storage is fast; no evidence of I/O pathology"],
 ],
 [0.20, 0.26, 0.30, 0.24]),
("h2", "10.3 Assessment and ideal layout"),
("p", lbl("INF") + " The current arrangement is already close to ideal: two-volume discipline (durable "
      + mono("noBackupFilesDir") + " vs disposable " + mono("cacheDir") + "), atomic promotion, cache "
      "outside the rootfs, /tmp rootfs-internal for isolation, sysdata as a rootfs sibling. " + lbl("REC") +
      " Only three refinements are worth making eventually: (1) an eviction policy for " +
      mono("apk-cache") + " (it grows unbounded today; apk itself never prunes beyond its own retention) "
      "with a Diagnostics-visible size row and a 'clean package cache' action; (2) a soft budget check "
      "before large installs (free-space gate already exists as reporting only); (3) keep WebView/Companion "
      "data where the platform puts it (default webview data dir) - moving it would fight the frozen "
      "renderer contract for zero benefit."),

# ============================ CHAPTER 11 ============================
("h1", "11. Process and Session Resource Model"),
("h2", "11.1 Scaling table (Phase J)"),
("table",
 ["Scenario", "OS processes", "PTY fds", "Java threads", "App-side heap (full scrollback)", "Guest RSS (proot+sh, est.)"],
 [
  ["1 host shell session", "1 (/system/bin/sh)", "1", "3", "~1.5 MB", "n/a (no proot)"],
  ["1 Linux session", "2 (proot -> sh)", "1", "3", "~1.5 MB", "low tens of MB total (est.)"],
  ["5 Linux sessions", "10", "5", "15", "~7.5 MB", "5x single"],
  ["10 Linux sessions", "20", "10", "30", "~15 MB", "10x single"],
  ["5 sessions + Companion (3 tabs)", "10 + WebView renderer proc(s)", "5", "15 + WebView pool threads", "~7.5 MB + WebView heaps", "+ renderer process"],
 ],
 [0.22, 0.18, 0.10, 0.12, 0.20, 0.18]),
("p", lbl("OBS") + " Scaling is linear and honest: no deduplication tricks, no shared reader, one proot "
      "chain per session, one PTY each, zero state sharing between sessions by construction. Headless "
      "package operations are separate short-lived proot processes (one per command), serialized by the "
      "apk lock, with the command probe now freshness-cached (60 s + in-flight guard, M5.1 F2). Nothing is "
      "suspended when the app is backgrounded: sessions keep running under the specialUse foreground "
      "service (that is the product promise), and ByteQueue backpressure is the only flow control. " +
      lbl("INF") + " The binding constraint at 10+ sessions is proot native RSS plus thread count, not "
      "Java heap. Duplicate-environment risks were audited and found structurally prevented: ONE shared "
      "apk cache (v0.4.1 lesson: a rootfs-internal stale cache once showed '31 distinct packages'), one "
      "install pipeline with refuse-to-overwrite promotion, probes bounded by freshness. " + lbl("REC") +
      " Add a soft cap notice at 8-10 concurrent sessions (honest warning, no hard limit) and keep the "
      "per-session model unchanged."),

# ============================ CHAPTER 12 ============================
("h1", "12. Companion / WebView Resource Model"),
("h2", "12.1 What exists (frozen architecture)"),
("p", lbl("OBS") + " The 'pool' is a " + mono("LinkedHashMap<String, WebView>") + " inside "
      "CompanionWebHost: one live WebView per OPEN tab, created lazily on first raise, kept until the tab "
      "is closed or the Activity is recreated. There is no cap, no LRU, no saveState/restore anywhere in "
      "the tree - the m4.1.0 design's 'active + 4 background LRU' pool and the m4.0 trim-destroy policy "
      "were RETIRED permanently by the m4.0.11 render verdict (they were part of the hosting stack proven "
      "to blank the canvas, and destroying user state behind their back was rejected as a product rule). "
      "A tab is a " + mono("TabRecord(defId, lastUrl)") + " - an identity plus a cold-restore URL anchor; "
      "switching is native view surgery on one stable FrameLayout canvas (removeAllViews + addView + "
      "onResume active + onPause others) - never a reload, never a recreation. The renderer is the "
      "contract-pinned m4.0.9 baseline: Activity-context WebView, plain FrameLayout, exactly four settings "
      "(JS on, DOM storage on, file access off, content access off), load after first layout, "
      "LOAD_NO_CACHE only for hard refresh, and zero diagnostics in the render path (" +
      cite("CompanionRenderContract.kt") + ", unit-pinned). " + mono("setRendererPriorityPolicy") + " is "
      "absent (android-36 stubs removed it; androidx.webkit is deliberately not carried) - the substitute "
      "is per-view onPause parking plus the onRenderProcessGone guard that destroys ONLY the crashed "
      "view and shows a failure card."),
("h2", "12.2 Lifecycle and memory posture (M5.1 state)"),
("p", lbl("OBS") + " Lifecycle mapping: Activity ON_PAUSE -> " + mono("pauseAll()") + " (per-view "
      "onPause + CookieManager flush, skipped entirely when no tabs exist); ON_RESUME -> " +
      mono("resumeActive()") + ". M5.1 F1: collapsing the sheet pauses EVERY tab (fully reversible - "
      "timers/layout/parsing suspended, cookies flushed, no reload on raise); raising wakes ONLY the "
      "active tab. M5.1 F4: onTrimMemory(>= TRIM_MEMORY_RUNNING_LOW) flushes cookies, gated on " +
      mono("hasLiveTabs()") + " so the WebView provider is never loaded by the trim path itself. No "
      + mono("pauseTimers()/resumeTimers()/evaluateJavascript") + " calls exist anywhere. " + lbl("INF") +
      " The memory stance is therefore: hold everything the user opened, persist sessions (cookies), let "
      "the system kill the renderer or the process if it must, and degrade honestly to the lastUrl "
      "anchor. CPU-safe with many tabs; memory-unbounded by design."),
("h2", "12.3 Drag behavior (verify: no resize thrashing)"),
("p", lbl("OBS") + " During a sheet drag the WebView's measured height is FROZEN at the last settled "
      "value, bottom-aligned and clipped inside the growing panel - the page never reflows under the "
      "finger; exactly one resize happens on release, and it is a layout pass of the same live instance, "
      "never a reload (" + cite("CompanionLayer.kt:84-89, 308-316") + "). The dedicated 40 dp handle "
      "separates tap (minimize) from slop-gated drag; at >= 0.90 settled fraction the tab strip becomes an "
      "additional vertical drag surface, armed mid-gesture, with taps/close/plus behaving normally. " +
      lbl("REC") + " This is already correct - explicitly on the do-not-optimize list."),
("h2", "12.4 Capacity analysis (labeled inference)"),
("p", lbl("UNK") + " No per-tab memory number exists in code or docs; the measurement gate "
      "(TESTING.md section 32.2E: 3-5 tabs, dumpsys meminfo, minimized-idle check) is still pending. "
      "The estimate below uses external typicals - Chromium WebView renderer heaps of ~80-250 MB for "
      "heavy SPA/AI-chat pages, shared across tabs within the provider's renderer processes - and MUST "
      "be replaced by measured numbers before any policy is enforced."),
("table",
 ["Device RAM", "Realistic simultaneous Companion tabs (heavy AI pages)", "Rationale / guardrail"],
 [
  ["4 GB", "2-3", "Android will already be near per-process LMK pressure; terminal + proot must keep ~500-800 MB headroom; rely on F1 pause + cookie durability"],
  ["6 GB", "4-6", "comfortable with pause-all-on-minimize; watch renderer-process growth, not just app PSS"],
  ["8 GB", "6-9", "policy cost becomes noticeable only if pages leak (SPA timers are paused, so growth is bounded)"],
  ["12 GB+", "10+", "effectively user-preference territory; still no hard cap"],
 ],
 [0.14, 0.34, 0.52]),
("p", lbl("REC") + " Recommended resource policy (no redesign): keep 'tabs live until closed' as the "
      "contract; keep F1/F4 as the entire background strategy; ADD (a) the pending measurement gate so "
      "the capacity table becomes measured fact, (b) a device-RAM-class awareness row in Diagnostics "
      "(informational), and (c) IF measurements prove pressure on 4 GB devices, prefer user-visible "
      "guidance (nudge to close inactive tabs) over any silent eviction - silent state destruction is "
      "the retired, rejected pattern. Do NOT reintroduce saveState/restore."),

# ============================ CHAPTER 13 ============================
("h1", "13. ARM64 Performance Risk Register"),
("p", "Per Phase L: no blind optimization. Each row carries its evidence and a priority; items marked "
      "MEASURE require the section-32 device gates before any code change. The M5.1 audit's "
      "already-sound list (documented in the afdf37a record) is repeated at the end as the "
      "do-not-optimize contract."),
("table",
 ["#", "Bottleneck", "Evidence", "Impact", "Priority", "Potential solution"],
 [
  ["1", "PRoot syscall overhead (ptrace stop/translate/continue)", "architectural (chapter 5); no on-device numbers yet", "compile/git/node-startup latency inside guest", "MEASURE (P1)", "in-guest microbenchmark vs host reference; publish expected-slowdown table; only then consider deeper work"],
  ["2", "Hidden-session output still parsed on main thread", "M5.1 F3 skips repaint only; emulator.append unconditional (TerminalSession.java:342-347)", "background cat/build streams burn main-thread budget; UI jank risk with N sessions", "P2", "defer/batch parse for non-visible sessions (bounded queue), preserving byte-order and resize semantics"],
  ["3", "TerminalBuffer.resize full reflow on column change", "upstream TerminalBuffer.java:196-246 (fast path only for same columns)", "pinch-zoom font steps can jank with large scrollback", "P3", "accept upstream behavior; debounce font-step commits; do not fork the emulator"],
  ["4", "Keyboard per-key composables + animations", "~50-70 composables, per-key coroutine + 2 animators", "measured cost trivial (M5.1 audit: 'keyboard allocations trivial')", "P3 / DO-NOT-TOUCH", "only revisit if profiling ever shows it; no redesign"],
  ["5", "Companion tab memory unbounded", "chapter 12 (one WebView per open tab, no cap)", "LMK pressure on 4-6 GB devices", "P1 MEASURE", "section 32.2E numbers first; then RAM-class guidance (no silent eviction)"],
  ["6", "Package probe spawn cost", "one proot exec per probe; fixed by M5.1 F2 (60 s freshness + in-flight guard)", "Home revisit cost already eliminated", "DONE (verify)", "keep; forced re-probe after package ops is correct"],
  ["7", "Startup eagerness", "none found: PocketShellApp.onCreate touches no webkit/guest; sessions spawn on user action only", "n/a", "DONE", "keep as regression contract (section 22 rule)"],
  ["8", "JNI transition frequency (per keystroke write)", "KeyEvent -> session.write -> 4 KiB ByteQueue -> writer thread", "bounded and small; upstream design", "n/a", "none"],
  ["9", "Companion drag recomposition", "frozen-height canvas; one resize on release", "none (already GPU-smooth by construction)", "DONE", "do-not-optimize"],
  ["10", "Guest-side toolchain performance (apk installs, compiles)", "under proot; multipliers unknown", "user-visible install times for gcc-class packages", "MEASURE (P2)", "same microbenchmark harness as #1; document expectations honestly in Packages UI if material"],
 ],
 [0.04, 0.22, 0.26, 0.18, 0.09, 0.21]),
("callout", "Do-not-optimize contract (M5.1 audit verdict).", "Lazy startup path; one WebView per tab with "
            "surgery-based switching (no recreation/reload); frozen-height drag; background-tab platform "
            "pause; process-scoped terminal sessions with 2000-row bound and no polling; one real Linux "
            "process per user action with self-stopping FGS; the frozen Companion baseline renderer; ONE "
            "keyboard; the theme system. None of these were found wasteful; re-opening them risks "
            "regressing proven behavior for negligible gain."),

# ============================ CHAPTER 14 ============================
("h1", "14. Security Model"),
("h2", "14.1 Android-level posture"),
("table",
 ["Aspect", "State"],
 [
  ["Permissions", "INTERNET (rootfs download only), ACCESS_NETWORK_STATE (device DNS discovery), FOREGROUND_SERVICE(_SPECIAL_USE) (session retention), POST_NOTIFICATIONS. No storage, no contacts, no location, no telemetry, no crash SDK"],
  ["App sandbox", "Standard untrusted_app_27 (targetSdk 28) domain; app uid (10xxx); all data under app-private dirs; no backup (allowBackup=false)"],
  ["Network", "Only dl-cdn.alpinelinux.org for the pinned rootfs; guest traffic is the user's own commands; no third-party endpoints"],
  ["Startup attack surface", "Application.onCreate touches no android.webkit (m4.0.1 rule, kept by F4 gating) - a broken WebView provider cannot crash launch"],
  ["Download trust chain", "rootfs: pinned URL + Content-Length cross-check + streaming SHA-256 vs constant; libapk patch: byte-scan pattern + tmp+rename staging + re-verify from disk; promotion: refuse-to-overwrite + post-promote sha re-assert"],
 ],
 [0.20, 0.80]),
("h2", "14.2 The uid=0 illusion (what proot root can and cannot do)"),
("p", lbl("OBS") + " Inside the guest, " + mono("id") + " reports uid=0(root) because proot's " +
      mono("--root-id") + " lies about getuid/geteuid and translates ownership checks - this is a "
      "userspace illusion for path/permission translation, NOT Android root. " + lbl("OBS") +
      " Recorded device evidence pins the boundary (SELinux neverallows, hidepid, /sys read walls)."),
("table",
 ['"root" inside the guest CAN', '"root" inside the guest CANNOT'],
 [
  ["Own the whole guest filesystem semantically (chown/chmod illusions, install packages, bind-mount emulations within its own tree)", "Read or touch any other app's data (kernel uid still 10xxx; SELinux app_data_file of other domains unreachable)"],
  ["See its OWN real process tree (the app's) with host PIDs - honest, via hidepid=2", "See other apps' processes (hidepid=2 hides other-uid /proc entries)"],
  ["Use the real network stack as the app (sockets permitted)", "Use raw sockets / ping-style setuid tricks (SELinux denies untrusted apps; ping must be http-based or busybox applet with no raw socket)"],
  ["Write anywhere inside rootfs + bound caches (real app_data_file writes)", "Write /proc/sys, /sys (read-only walls), or mount anything for real (mount(2) is per-session emulation)"],
  ["Run any musl/static binary stored in its tree (domain exemption)", "Escalate to real root, load kernel modules, or bypass SELinux - there is no path from untrusted_app_27 to privileged domains without an exploit (out of scope and out of threat model)"],
 ],
 [0.50, 0.50]),
("p", lbl("INF") + " Downloaded binaries: the platform's own downloads are pinned and verified; "
      "user-directed downloads inside the guest are the user's commands by product definition - the "
      "Android sandbox (plus the no-exec-on-shared-storage rule) is the containment boundary. Secrets: "
      "the spec environment carries no secrets (paths and locale only); guest-visible env is standard "
      "PATH/HOME/TERM. Arbitrary command execution IS the product; its blast radius is deliberately "
      "equal to the app sandbox, which is the strongest statement available without root or a real "
      "container."),
]

# ============================ CHAPTER 15 ============================
def q(n, text):
    return ("n", [f"<b>Q{n}.</b> {text}"])

BLOCKS += [
("h1", "15. Answers to the 30 Platform Questions"),
("p", "Each answer is self-contained and evidence-labeled. Cross-references point to the chapter with "
      "the full argument."),
q(1, "<b>What exactly is the current PocketShell Linux architecture?</b> " + lbl("OBS") + " A native "
     "Android app (targetSdk 28) whose Compose UI owns sessions through a process-scoped manager; the "
     "vendored Termux terminal engine allocates a real PTY via JNI and forks proot (self-compiled Termux "
     "fork v5.1.107.92) from nativeLibraryDir; proot translates paths into an extracted, sha256-pinned "
     "Alpine 3.24.1 minirootfs under noBackupFilesDir, binding /dev, /proc (interactive only), /sys and "
     "app-owned apk caches per session; the guest shell is busybox ash on musl; apk inside the guest does "
     "package management, protected from the SELinux linkat neverallow by a no-/proc commit path for app "
     "operations and a self-repairing one-byte fd-link patch for in-guest apk (chapters 3, 5-7)."),
q(2, "<b>Which components are inherited from Termux?</b> " + lbl("OBS") + " The terminal emulator and "
     "terminal view (vendored verbatim @ 3b66f879, GPLv3, 19 test classes), the PTY JNI (termux.c), and "
     "the proot fork (termux/proot). Nothing else - the session manager, launcher, installer, package "
     "gateway, keyboard, and Companion are original PocketShell code; the sysdata overlay model was "
     "adapted from proot-distro (studied, not copied)."),
q(3, "<b>Which components are inherited from PRoot?</b> " + lbl("OBS") + " Path translation, the uid-0 "
     "illusion (--root-id), per-process bind emulation, --kill-on-exit lifecycle, the link2symlink "
     "extension, and the split-loader mechanism (PROOT_LOADER/32). All guest filesystem virtualization "
     "is proot's."),
q(4, "<b>Which limitations are caused by PRoot?</b> " + lbl("INF") + " Syscall overhead from ptrace "
     "(the measurable one), no PID namespace (host-global PIDs), per-session bind semantics (mount(2) "
     "emulation never persists), and the .l2s symlink-anchor artifact of link2symlink. See 5.3."),
q(5, "<b>Which limitations are caused by Alpine/musl?</b> " + lbl("OBS") + " The glibc binary "
     "incompatibility class (chapter 8) and the resulting block on first-party vendor CLI distributions. "
     "apk's O_TMPFILE/linkat commit style also interacted with SELinux - an apk-tools design meeting "
     "Android policy, not a musl defect."),
q(6, "<b>Which limitations are caused by Android SELinux?</b> " + lbl("OBS") + " The execute_no_trans "
     "neverallow (hence targetSdk 28 being load-bearing), the link()/linkat() neverallow (shaped the "
     "entire /proc and apk-commit story), /proc version/kernel-node read walls (One UI proc_version), "
     "noexec shared storage, and hidepid=2 process isolation (kernel, surfaced through the same wall)."),
q(7, "<b>Which /proc limitations are fundamentally unavoidable?</b> " + lbl("OBS") + " Cross-uid process "
     "visibility, kcore/kmsg/sysrq, writable /proc/sys, and any real per-guest namespace view - all "
     "require root or a kernel boundary (7.4)."),
q(8, "<b>Which /proc limitations can be improved?</b> " + lbl("REC") + " More probe-first sysdata "
     "overlays for standard files Android denies, richer synthesized contents (already honest: real "
     "btime, real core count, real uptime), and /proc/sys read-only emulations if tooling needs them - "
     "all inside the existing, self-repairing overlay mechanism (7.4)."),
q(9, "<b>Can we support glibc ARM64 binaries?</b> Yes - via the sidecar, not via gcompat. " + lbl("OBS") +
     " gcompat is proven insufficient (8.2); real glibc works (Antigravity ran correctly on Debian "
     "glibc in rehearsal)."),
q(10, "<b>What is the cleanest way?</b> " + lbl("REC") + " A PocketShell-owned glibc prefix "
      "(/pocketshell/runtime/glibc-aarch64) provisioned through the existing pinned-download pipeline, "
      "plus per-ELF-interpreter launcher shims so users just run the tool (8.5)."),
q(11, "<b>Can we ship a small glibc runtime?</b> " + lbl("INF") + " Yes: loader + libc + libstdc++ + "
      "libgcc is on the order of 15-25 MB compressed / 50-80 MB installed (estimate, to be measured); a "
      "full Debian minbase is larger (40-60 MB compressed). The libs-only sidecar is the right first "
      "payload."),
q(12, "<b>Can musl and glibc coexist in one PocketShell environment?</b> " + lbl("INF") + " Yes - per "
      "process, never mixed: the glibc loader with --library-path isolates its own dependency set; apk "
      "musl tools are untouched. One guest, two libcs, invisible selection (8.5)."),
q(13, "<b>Can users compile native ARM64 software themselves?</b> " + lbl("OBS") + " Yes for musl "
      "targets today (apk gcc/clang/binutils extract successfully under link2symlink - device-proven "
      "after the v0.6.1 hardlink incident)."),
q(14, "<b>Can compiled binaries execute reliably?</b> " + lbl("OBS") + " Yes - same rules as any guest "
      "binary (app_data exec in the untrusted_app_27 domain, chapter 6); glibc-TARGETING builds need the "
      "sidecar's loader/headers."),
q(15, "<b>Can we support prebuilt developer CLIs without user workarounds?</b> " + lbl("REC") + " That "
      "is precisely Runtime 2.0's goal: ship/provision the runtime so the user installs and runs; the "
      "Antigravity/Kilo sagas define the two bug classes (libc and procfs) already closed or closeable "
      "(chapters 8, 17)."),
q(16, "<b>What should PocketShell ship by default?</b> " + lbl("REC") + " In-APK: proot stack, patched "
      "libapk asset, terminal engine, fonts (as today). First-install download: pinned Alpine rootfs "
      "(as today) + the glibc sidecar payload (new, same trust chain). Everything else package-managed."),
q(17, "<b>What should be package-managed?</b> " + lbl("REC") + " All musl tooling via apk (unchanged); "
      "glibc-side developer stacks (Node, agent CLIs) as versioned, sha-pinned sidecar bundles with an "
      "in-app updater path that reuses the rootfs pipeline; nothing hand-patched by users."),
q(18, "<b>Should we continue using Alpine?</b> " + lbl("REC") + " Yes. It is small (9.3 MB extracted), "
      "fast, pinned, and its apk ecosystem covers the musl world; nothing observed argues for a distro "
      "change. The glibc gap is solved by the sidecar, not by switching distros."),
q(19, "<b>Should we change the distro?</b> " + lbl("REC") + " No. A second distro rootfs remains an "
      "escalation path only if the sidecar proves insufficient (8.5)."),
q(20, "<b>Should we improve PRoot?</b> " + lbl("REC") + " Keep the pinned fork, adopt newer upstream "
      "releases deliberately, apply only documented upstreamable patches (5.3)."),
q(21, "<b>Should we eventually replace PRoot?</b> " + lbl("REC") + " Not on current evidence. A "
      "replacement (own tracer/container/VM) is justified only by a measured bottleneck the microbench "
      "cannot explain, or by a product need for real namespaces (7.4/5.3)."),
q(22, "<b>Which limitations require native Android code?</b> " + lbl("INF") + " None of the current "
      "compatibility ones - exec/link/proc constraints are policy, and the fixes (sidecar, overlays, "
      "launchers) are userspace. Native Android code matters only for lifecycle performance work "
      "(hidden-tab parse batching) and any future binder/aidl-based features."),
q(23, "<b>Which limitations can be solved entirely in userspace?</b> " + lbl("INF") + " glibc "
      "compatibility (sidecar), /proc file walls (overlays), hardlink policies (link2symlink + "
      "workarounds), package cache eviction, launcher selection, all performance items except proot "
      "syscall cost itself."),
q(24, "<b>What is the biggest architectural risk right now?</b> " + lbl("REC") + " The single-libc "
      "guest (F1) - it blocks the product's core promise (prebuilt developer CLIs). Second: the "
      "targetSdk-28 dependency, which is safe only as long as the app remains side-loaded (F8)."),
q(25, "<b>What should M5.1 actually optimize?</b> " + lbl("OBS") + " It already happened (afdf37a, "
      "F1-F4) and was audit-first as mandated; the remaining evidence-driven items are chapter 13's "
      "MEASURE rows (proot microbench, section 32.2E tab memory) and the P2 hidden-tab parse batching."),
q(26, "<b>What should NOT be optimized because it is already good?</b> " + lbl("OBS") + " The entire "
      "do-not-optimize contract (13, callout): lazy startup, Companion switch surgery, frozen-height "
      "drag, terminal session model, one-keyboard dispatch, package-op profile."),
q(27, "<b>How do we prevent multiple Companion tabs from hurting terminal performance?</b> " + lbl("REC") +
     " The current design already isolates them: WebViews render in the provider's sandboxed renderer "
     "processes (separate from the app UI process), F1 pauses everything when minimized, only the active "
     "tab runs, and the terminal renders in its own view. Keep it; add the measurement gate; if needed, "
     "RAM-class guidance - never let a WebView policy touch the terminal's process priority."),
q(28, "<b>How do we maintain a small and fast APK while shipping runtime capabilities?</b> " + lbl("REC") +
     " Keep the APK lean (22.6 MB today: dex + proot stack + fonts), push everything optional into "
     "pinned, verifiable downloads (rootfs, sidecar, tool bundles); the trust pipeline (size+sha256+"
     "staged promote) is already built and reusable."),
q(29, "<b>What compatibility layer should PocketShell own itself?</b> " + lbl("REC") + " The glibc "
      "sidecar prefix + launcher shims + the sysdata overlay set + the apk fd-link self-repair. These "
      "are product-defining, small, and version-resilient when built on patterns rather than checksums."),
q(30, "<b>What should remain delegated to upstream?</b> " + lbl("REC") + " The terminal engine and view "
      "(re-sync from Termux), proot itself (rebuild from termux/proot), talloc, Alpine/apk packages, "
      "Chromium/WebView (platform), Compose/androidx. PocketShell integrates and pins; it does not "
      "re-implement what mature upstreams maintain (Phase Q ladder)."),
]

# ============================ CHAPTER 16 ============================
BLOCKS += [
("h1", "16. KEEP / MODIFY / ADD / REPLACE + Open-Source Strategy"),
("h2", "16.1 Categorization (Phase R)"),
("table",
 ["Category", "Items"],
 [
  ["KEEP (proven, frozen)", "PRoot + pinned fork + argv shape; Alpine 3.24.1 rootfs pipeline; vendored terminal stack and tests; targetSdk 28 + useLegacyPackaging; the two launch profiles and their guards; Companion frozen baseline renderer + tab surgery + F1/F4 lifecycle; custom keyboard + dispatch chain; RuntimeInstaller trust chain; delivery/mirror ritual; diagnostics honesty model"],
  ["MODIFY (small, evidence-gated)", "onCodePoint ctrl/alt map (return false for unmapped); cursor-hidden blinker handling; hidden-session parse batching (P2); apk-cache eviction policy; Diagnostics additions (RAM class, proot microbench results, sidecar state); soft session-count notice"],
  ["ADD (Runtime 2.0 scope)", "glibc-aarch64 sidecar prefix + pinned payload + launcher shims; sidecar bundle format (manifest, sha256s, rollback) reusing the rootfs pipeline; Diagnostics rows for sidecar state; device measurement gates as permanent fixtures (section 32)"],
  ["REPLACE (nothing now; escalation paths only)", "PRoot replacement (only on measured, unfixable bottleneck); second distro rootfs (only if sidecar insufficient); saveState/restore tab policy (retired - would replace the honest anchor model with the previously broken one)"],
  ["DO NOT TOUCH", "CompanionRenderContract pins; ONE keyboard; launch-profile derivation (procEnabled); the fail-loud procContractProblem; GuestApkCompat pattern rules; startup laziness (section 22); Theme system; package-op no-/proc require-guard"],
 ],
 [0.24, 0.76]),
("h2", "16.2 Open-source strategy ladder applied (Phase Q)"),
("p", "For every proposed solution, the preference order is: (1) upstream project directly, (2) "
      "dependency/module reuse, (3) small PocketShell integration, (4) fork with documented patch, "
      "(5) rewrite as last resort. " + lbl("REC") + " Applied: glibc runtime -> do NOT write one; reuse "
      "Debian/Ubuntu's published glibc artifacts (upstream binaries) under a thin pinned payload "
      "(integration level 3). Launcher shims -> standard ld.so mechanics, no new infrastructure (level "
      "3). Terminal/PTY -> Termux upstream re-sync procedure already defined (level 1). PRoot -> termux/"
      "proot upstream + documented micro-patches (level 4, already practiced). Sysdata overlays -> "
      "proot-distro's model, already re-implemented narrowly (level 3, documented attribution). "
      "Node/Python/toolchains -> apk upstream packages (level 1). Nothing on the roadmap requires a "
      "rewrite."),
]

# ============================ CHAPTER 17 ============================
BLOCKS += [
("h1", "17. PocketShell Runtime 2.0 - Proposed Architecture"),
("h2", "17.1 Architecture"),
("code",
"""+-----------------------------------------------------------------------------------------+
| PocketShell app (untrusted_app_27, targetSdk 28, side-loaded, GPLv3)                    |
|                                                                                         |
|  Compose UI / ONE keyboard / session manager / Diagnostics                              |
|        |                                        |                                       |
|  vendored Termux terminal                 Companion host (FROZEN baseline)              |
|  (PTY via libtermux.so JNI)               one WebView per open tab, F1/F4 lifecycle     |
|        | exec (path-based)                      |                                       |
|        v                                        v                                APK   |
|  nativeLibraryDir: libproot.so + loader(s) + libtalloc.so   [Chromium provider proc]    |
|        |                                                                                |
|        v  ptrace path translation, per-session binds, --root-id, --kill-on-exit         |
|  +-----------------------------------------------------------------------------------+ |
|  | Alpine 3.24.1 guest (musl, sha-pinned download)   noBackupFilesDir/runtime/rootfs | |
|  |   /bin,/usr: busybox + apk world (node, python, git, gcc, neovim, tmux, ssh)      | |
|  |   /proc: HOST procfs (hidepid=2) + 5 sysdata overlays (probe-first)               | |
|  |   /etc/apk/cache -> app-owned host binds                                          | |
|  |   NEW  /pocketshell/runtime/glibc-aarch64/                                        | |
|  |          ld-linux-aarch64.so.1 + libc/libm/libpthread/libdl + libstdc++/libgcc    | |
|  |          provisioned like the rootfs: pinned URL + size + sha256, staged promote  | |
|  |          launcher shims in /usr/local/bin select loader per ELF interpreter       | |
|  +-----------------------------------------------------------------------------------+ |
+-----------------------------------------------------------------------------------------+
     exec policy: only nativeLibraryDir (app) + app_data via untrusted_app_27 exemption""",
 "Runtime 2.0: the only structural addition is the glibc sidecar prefix; everything else is the "
 "audited, proven architecture."),
("h2", "17.2 Component decisions"),
("table",
 ["Component", "Runtime 2.0 decision"],
 [
  ["Base userspace", "Alpine stays (Q18/Q19); guest contract unchanged (/proc unconditional, no-/proc package ops)"],
  ["libc strategy", "musl primary + real-glibc sidecar (option E: C + apk + selective static later); gcompat rejected"],
  ["Loader strategy", "per-ELF-interpreter launcher shims; two-liner wrappers; sidecar loader path never exposes complexity to the user"],
  ["PRoot strategy", "keep pinned fork v5.1.107.92; deliberate upstream adoption; documented patches only"],
  ["Native runtime", "no new native code beyond the existing terminal JNI; performance work happens in Kotlin/Compose/guest"],
  ["Binary compatibility", "musl: apk; glibc: sidecar; static: works; downloaded: works (musl or via sidecar)"],
  ["Package management", "apk unchanged; NEW sidecar bundle installer reusing RuntimeInstaller (pinned, verified, staged, atomic); bundles carry manifest + per-file sha256"],
  ["Process management", "unchanged (one chain per session, FGS retention, waiter reaping); soft session notice only"],
  ["Terminal integration", "unchanged; ctrl-map fix inside the existing client hook"],
  ["Storage", "unchanged layout + sidecar dir + apk-cache eviction; same two-volume discipline"],
  ["Permissions", "unchanged five permissions"],
  ["Resource management", "measurement gates (section 32) -> RAM-class guidance; no silent eviction; soft session notice"],
 ],
 [0.22, 0.78]),
("h2", "17.3 Prebuilt runtime strategy (Phase G)"),
("table",
 ["Category", "Contents", "Delivery", "Size (est.)", "License handling", "Integrity / update / rollback"],
 [
  ["In-APK", "proot stack (4 ABIs), patched libapk asset, terminal JNI, fonts", "already shipped", "~2.6 MB of the 22.6 MB APK (libs+asset)", "GPL-2.0/GPLv3/OFL notices; GPLv3 whole-app consequence documented", "APK sha256 pin per release (existing ritual)"],
  ["Core payload (first install)", "Alpine minirootfs", "download at install (existing)", "4.02 MB / 9.3 MB", "musl MIT, busybox GPL-2.0, notices inside rootfs", "size + SHA-256 pin; re-install = rollback; metadata re-assert after promote"],
  ["Sidecar payload (new)", "glibc loader + libc/libm/libpthread/libdl/librt/resolv + libstdc++ + libgcc (+ optional locale archive)", "download on demand, same pipeline", "~15-25 MB compressed / 50-80 MB installed", "LGPL-2.1/GPL glibc notices bundled; static-link exception note where relevant", "per-bundle manifest sha256s; keep previous version dir; rollback = re-point symlink/re-promote previous manifest"],
  ["Tool bundles (new)", "glibc Node runtime, agent CLIs (Cline/Claude Code class) installed into sidecar", "package-managed via bundle installer", "Node ~25-35 MB compressed; agents vary", "upstream licenses per bundle manifest", "versioned bundles; explicit-name withdrawal mirrors (existing ritual); no user hand-patching"],
  ["apk world", "everything musl", "apk inside guest (existing)", "user-driven", "apk info metadata (existing)", "apk transactional journal + app cancel/verify flow (existing)"],
 ],
 [0.15, 0.24, 0.14, 0.13, 0.16, 0.18]),
("p", lbl("REC") + " Sizing note: the two-stage approach keeps the APK under ~23 MB while making the "
      "total first-use footprint roughly 40-120 MB depending on sidecar breadth - comparable to Termux "
      "bootstrap, and every downloaded byte crosses the same verified pipeline. " + lbl("UNK") +
      " Exact sidecar size and glibc version choice (Debian 12/13 or Ubuntu 24.04 glibc builds) are "
      "decisions to finalize with the guest-side Kilo report and a provisioning rehearsal."),

# ============================ CHAPTER 18 ============================
("h1", "18. Prioritized Roadmap"),
("table",
 ["Phase", "Items", "Gate"],
 [
  ["P0 - Evidence + correctness (no features)", "Run TESTING section 32 scenarios A-G (before/after numbers for F1-F4); in-guest proot microbenchmark vs host; fix onCodePoint ctrl/alt map; cursor-hidden blinker fix; record Companion per-tab memory (32.2E); cross-check this report against the Kilo guest-side forensic report", "numbers recorded, two terminal fixes shipped in a regular patch release"],
  ["P1 - glibc sidecar MVP", "Define sidecar payload (glibc version, file list, manifest format); implement provisioning via RuntimeInstaller pipeline; launcher shim mechanics; Diagnostics rows; ONE flagship tool validated end-to-end (official Node or one agent CLI); rollback path exercised", "sidecar install/rollback verified on device; flagship tool runs without user workarounds"],
  ["P2 - Resource policy (measurement-driven)", "RAM-class guidance in Diagnostics; hidden-session parse batching if P0 shows main-thread cost; apk-cache eviction with visible size; soft session notice", "no regression in section 32 matrix after changes"],
  ["P3 - Developer experience", "In-guest toolchain polish (gcc/clang/headers via apk), glibc-targeting build docs, UV_LINK_MODE-class caveats surfaced in docs not UI", "documentation + package availability"],
  ["P4 - Long-term watch", "Android 16+ behavior for legacy targetSdk apps; upstream proot releases; app_exec_data_file flag evolution; upstream musl builds of key agent CLIs; second-rootfs escalation only if sidecar insufficient", "watchlist reviewed each milestone"],
 ],
 [0.20, 0.62, 0.18]),
("callout", "Standing constraint.", "No new features during performance phases; no replacement of the "
            "Companion baseline renderer, browser engine, terminal engine, keyboard, or provider/login "
            "behavior; no targetSdk, distro, or PRoot changes outside the decision points above."),

# ============================ CHAPTER 19 ============================
("h1", "19. Unknowns Requiring Device Experiments"),
("n", [
  "<b>Companion per-tab memory.</b> TESTING section 32.2E pending - dumpsys meminfo with 3-5 heavy tabs, "
  "minimized-idle CPU/memory check, tab-switch invariance. Every capacity number in 12.4 is inference "
  "until then.",
  "<b>PRoot overhead multiplier.</b> In-guest vs host-reference microbenchmarks (syscall-heavy, exec-heavy, "
  "IO-heavy workloads) on SM-F711B; thermal/governor effects on sustained compiles.",
  "<b>Guest-side corroboration.</b> The Kilo/MiniMax in-guest report may reveal /proc, mount table, apk "
  "state, and linker behaviors invisible from the host-side audit; reconcile the two reports' /proc file "
  "lists and any extra SELinux denials.",
  "<b>OEM/One UI variance.</b> proc_version denial and Force-Dark defaults were observed on Samsung/One UI "
  "API 35; other OEMs may differ (matters for the sysdata overlay set and Companion darkening guards).",
  "<b>glibc sidecar practicals.</b> Which glibc build to pin, its real extracted size, NSS/locale behavior "
  "on Android, and whether any target CLI needs /etc beyond resolv.conf - a provisioning rehearsal answers "
  "all four.",
  "<b>Filesystem atomicity classes.</b> rename-promotion atomicity is assumed same-volume; verify on the "
  "device's actual f2fs/ext4 layout and after future Android storage changes.",
  "<b>Hidden-session parse cost.</b> Measure main-thread time attributable to background-session emulator "
  "append under streaming output (decides whether P2 batching is worth its complexity).",
  "<b>Upstream musl availability drift.</b> Whether key agent CLIs start publishing official musl/ARM64 "
  "artifacts (would reduce sidecar urgency) - network-dependent, check per release.",
  "<b>Android 16+ legacy-target outlook.</b> Side-load install rules for targetSdk-28 apps on future "
  "Android versions; Play policies are irrelevant but OS-side install blockers would be existential.",
]),
]


# ============================ APPENDIX A ============================
BLOCKS += [
("h1", "Appendix A. Primary Evidence Index"),
("p", "A compact pointer table for cross-report work: where each architectural claim lives in the "
      "repository. Line numbers refer to the audited tree (main @ 7364452)."),
("table",
 ["Claim area", "Primary evidence (file / artifact)"],
 [
  ["Launch argv, profiles, /proc derivation, guards", "app/src/main/java/app/pocketshell/runtime/RuntimeProcessLauncher.kt:127-404; docs/PROCFS-CONTRACT.md; docs/M2-ARCHITECTURE.md sections 7-12"],
  ["PTY + fork/exec internals", "terminal-emulator/src/main/jni/termux.c (create_subprocess, setPtyWindowSize, waitFor); TerminalSession.java:82-364"],
  ["Session ownership + FGS retention", "app/.../terminal/TerminalSessionManager.kt:23-290; TerminalService.kt:31-96; AndroidManifest.xml:60-67"],
  ["targetSdk 28 rationale (W^X)", "app/build.gradle.kts:16-24; docs/M2-RESEARCH.md sections 1.1-1.3 (AOSP app_neverallows.te quote); v0.3.0/v0.3.1 incident records in worklog"],
  ["apk fd-link patch + self-repair", "app/.../runtime/GuestApkCompat.kt:50-268; scripts/patch_apk_fdlink.py; assets/guest/libapk.so.3.0.0.fdlinkoff.aarch64 (sha256 b8cd95e2...)"],
  ["Sysdata overlays", "app/.../runtime/GuestSysDataCompat.kt:85-446 (probe-first rule, contents, write hardening)"],
  ["Installer + pins", "app/.../runtime/RuntimeManager.kt:22-45 (Alpine pin), RuntimeInstaller.kt:57-362, RuntimeStorage.kt, RuntimeChecksum.kt"],
  ["Package layer honesty model", "app/.../packages/AlpinePackageManager.kt, PackageGateway.kt:87-415, PackageOperationManager.kt, GuestCommandRunner.kt"],
  ["Command apps + launch chain", "app/.../apps/CommandApps.kt:53-168; TerminalSessionManager.kt:126-158 (guestLaunchChain argv)"],
  ["Companion frozen contract + lifecycle", "app/.../companion/CompanionRenderContract.kt (pins), CompanionWebHost.kt:100-439, ui/companion/CompanionLayer.kt:84-451; docs/PHASE-4-COMPANION-DESIGN.md sections 8, 22, 25"],
  ["M5.1 fixes F1-F4", "commit afdf37a (git show; diffstat 10 files +266/-6); docs/CHANGELOG.md 0.9.1; docs/ROADMAP.md:736-775"],
  ["Device incidents + gates", "docs/TESTING.md sections 8, 10, 12, 16, 32; docs/M2.6-RESEARCH.md sections 4, 7-8; docs/ANTIGRAVITY-PLATFORM.md sections 2-6; worklog.md (1,191 lines)"],
  ["Third-party pins + licenses", "docs/THIRD_PARTY.md (complete); docs/licenses/proot-COPYING-GPL-2.0; gradle/libs.versions.toml"],
 ],
 [0.34, 0.66]),
]

# ============================ BUILDER ============================
def esc(t):
    return str(t).replace("&", "&").replace("<", "<").replace(">", ">")

def make_table(header, rows, ratios, align_center=None):  # escaped override
    align_center = align_center or []
    data = []
    if header:
        hrow = []
        for i, h in enumerate(header):
            st = S["cellHC"] if i in align_center else S["cellH"]
            hrow.append(Paragraph(f"<b>{esc(h)}</b>", st))
        data.append(hrow)
    for r in rows:
        row = []
        for i, c in enumerate(r):
            st = S["cellC"] if i in align_center else S["cell"]
            row.append(Paragraph(esc(c), st))
        data.append(row)
    widths = [ratio * CONTENT_W for ratio in ratios]
    assert sum(widths) <= CONTENT_W + 0.5, "table wider than available width"
    t = Table(data, colWidths=widths, hAlign="CENTER", repeatRows=1 if header else 0)
    style = [("VALIGN", (0, 0), (-1, -1), "TOP"),
             ("GRID", (0, 0), (-1, -1), 0.5, BORDER),
             ("LEFTPADDING", (0, 0), (-1, -1), 5), ("RIGHTPADDING", (0, 0), (-1, -1), 5),
             ("TOPPADDING", (0, 0), (-1, -1), 4), ("BOTTOMPADDING", (0, 0), (-1, -1), 4)]
    if header:
        style.append(("BACKGROUND", (0, 0), (-1, 0), HEADER_FILL))
        start = 1
    else:
        start = 0
    for i in range(start, len(data)):
        style.append(("BACKGROUND", (0, i), (-1, i),
                      TABLE_STRIPE if (i - start) % 2 == 1 else colors.white))
    t.setStyle(TableStyle(style))
    return t

def build_story():
    from reportlab.platypus.frames import Frame
    from reportlab.platypus.doctemplate import PageTemplate
    story = []
    story.append(Paragraph("<b>Table of Contents</b>", S["tocTitle"]))
    toc = TableOfContents()
    toc.levelStyles = [S["toc0"], S["toc1"]]
    story.append(toc)
    story.append(PageBreak())
    story.append(BodyStartMarker(PAGE_STATE))
    for blk in BLOCKS:
        kind = blk[0]
        if kind == "h1":
            story.append(CondPageBreak(H1_ORPHAN))
            story.append(KeepTogether([heading(blk[1], S["h1"], 0), rule()]))
        elif kind == "h2":
            story.append(CondPageBreak(AVAIL_H * 0.12))
            story.append(heading(blk[1], S["h2"], 1))
        elif kind == "h3":
            story.append(Paragraph(f"<b>{blk[1]}</b>", S["h3"]))
        elif kind == "p":
            story.append(Paragraph(blk[1], S["body"]))
        elif kind == "pl":
            story.append(Paragraph(blk[1], S["bodyL"]))
        elif kind == "n":
            for item in blk[1]:
                story.append(Paragraph(item, S["num"]))
            story.append(Spacer(1, 4))
        elif kind == "b":
            for item in blk[1]:
                story.append(Paragraph(item, S["bullet"], bulletText="\u2022"))
            story.append(Spacer(1, 4))
        elif kind == "table":
            story.append(Spacer(1, 8))
            story.append(make_table(blk[1], blk[2], blk[3]))
            story.append(Spacer(1, 10))
        elif kind == "code":
            story.extend(code_block(blk[1], blk[2] if len(blk) > 2 else None))
        elif kind == "stats":
            story.extend(stat_row(blk[1]))
        elif kind == "callout":
            story.extend(callout(blk[1], blk[2]))
        elif kind == "sp":
            story.append(Spacer(1, blk[1]))
    return story

def main():
    out_dir = "/home/z/my-project/download"
    os.makedirs(out_dir, exist_ok=True)
    out = os.path.join(out_dir, "PocketShell-Runtime-Forensic-Audit.pdf")
    doc = TocDocTemplate(out, pagesize=A4,
                         leftMargin=MARGIN, rightMargin=MARGIN,
                         topMargin=MARGIN, bottomMargin=MARGIN,
                         title=DOC_TITLE, author="Z.ai", creator="Z.ai",
                         subject="Forensic architecture audit of PocketShell v0.9.1-m5.1.0 (vc39)")
    from reportlab.platypus.frames import Frame
    from reportlab.platypus.doctemplate import PageTemplate
    frame = Frame(MARGIN, MARGIN, AVAIL_W, AVAIL_H, id="normal",
                  leftPadding=0, rightPadding=0, topPadding=0, bottomPadding=0)
    # SimpleDocTemplate.build() installs its own First/Later page templates;
    # multiBuild forwards buildKwds to build, so pass footers there instead of
    # addPageTemplates (which only survives page 1).
    doc.multiBuild(build_story(), onFirstPage=_footer, onLaterPages=_footer)
    print("BUILD OK:", out)

if __name__ == "__main__":
    main()
