# Agent B UI/UX Polish Specification & Implementation

## Overview

This document specifies the UI/UX polish changes implemented by Agent B (Antigravity CLI pair assistant) on branch `agent-B/ui-polish` for PocketShell M7.2.

All changes adhere strictly to the role boundary of small UI/UX polish without modifying terminal session lifecycle, PTY runtime, notification dispatching, or agent detection architecture.

---

## 1. Companion Sheet & Drag Bar Polish

### Problem
1. **Drag Bar Tap Toggle**: Tapping the companion drag handle when minimized did not reopen the sheet to the user's previously chosen height.
2. **Remembered Height**: Reopening the companion sheet repeatedly snapped to a hardcoded default height (0.55f), discarding user adjustments.
3. **Bloated Header**: Applying container background color to the handle zone created a visually thick 74dp white bar instead of a compact, native tab strip matching standard tab height (34dp).

### Implementation
- **Tap Toggle & Height Restoration** (`CompanionLayer.kt`, `CompanionViewModel.kt`, `CompanionRepository.kt`):
  - A single tap on the drag bar toggles between minimized (`CLOSED`) and the user's last remembered height (`lastExpandedHeight`).
  - `lastExpandedHeight` is persisted in preferences (`DataStore`) and clamped between `MIN_PANEL_FRACTION` (0.25f) and `MAX_PANEL_FRACTION` (0.90f), defaulting to `HALF` (0.55f).
  - Normal dragging continues to update and persist the settled height without resetting to defaults.
- **Compact Header ("Same Height as Tab")** (`CompanionTabStrip.kt`, `CompanionLayer.kt`):
  - Removed container background fill from the outer drag bar column to eliminate the bloated 74dp white header.
  - Set `CompanionTabStrip` directly to `34.dp` height with `RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)` matching `HomeTokens.heroRadius` and a subtle 1dp `TerminalTheme.divider` border.
  - Added `CompanionEmptyState` rounded top border to ensure visual consistency when no companion tabs are active.

---

## 2. Terminal Background & Palette Synchronization

### Problem
In light mode (Daylight Sapphire), the terminal emulator canvas previously defaulted to pure black or dark navy, creating an incongruous visual experience when switching the app theme to light mode.

### Implementation
- **Light Theme Palette Tokens** (`TerminalPalette.kt`, `TerminalTheme.kt`):
  - In light theme:
    - Canvas Background: Off-white Daylight Sapphire (`0xFFF7F9FC`).
    - Foreground Text: Midnight Blue (`0xFF17233B`).
    - Cursor: Deep Slate Blue (`0xFF3D5A96`).
    - High-contrast ANSI colors tuned specifically for daylight readability.
  - In dark theme:
    - Canvas Background: Midnight Navy (`0xFF0B101D`).
    - Foreground Text: Soft Ice Sapphire (`0xFFDCE6F8`).
    - Cursor: Accent Cyan (`0xFF4DA3FF`).
- **Live Theme Synchronization** (`TerminalScreen.kt`):
  - In `TerminalScreen`, the `AndroidView` `update` callback observes theme changes and invokes `TerminalPalette.applyDefaults(light)` alongside `terminalView.setBackgroundColor(TerminalTheme.canvas.toArgb())` to ensure instant and complete re-theming without requiring session resets.

---

## 3. Theme-Matched Monochrome Brand Logos

### Problem
The launcher and companion tabs previously contained mixed, colorful brand icons or icons with inconsistent solid backgrounds that clashed with the minimalist Midnight / Daylight design language. Additionally, web companions and CLI tools used disconnected visual assets.

### Implementation
- **Theme Color Alignment** (`scripts/make_launcher_icons.py`):
  - **Light Theme Logos**: Rendered in Midnight Blue (`#17233B` / RGB `(23, 35, 59)`).
  - **Dark Theme Logos**: Rendered in Soft Light Sapphire (`#DCE6F8` / RGB `(220, 230, 248)`).
  - **Transparent Canvases**: All icons generated on transparent backgrounds to blend seamlessly into launcher grids and tab strips.
  - **Cutout Preservation**: For marks with interior negative space (e.g. `builtin-zai` and `opencode`), alpha luminance masking preserves the clean transparent cutouts.
- **Unified Brand Assets**:
  - Claude Web companion (`builtin-claude`) and Claude CLI (`claude`) now share the official Anthropic starburst SVG mark (`builtin-claude.svg`).
  - ChatGPT web companion (`builtin-chatgpt`) and Codex CLI (`codex`) share the unified OpenAI glyph.
  - Z.ai web companion (`builtin-zai`) and ZCode CLI (`zcode`) share the unified Z.ai glyph.
- **Regenerated Assets**:
  - All 26 WebP marks across dark and light variants in `app/src/main/assets/launcher_icons/` have been regenerated and verified.

---

## 4. Verification & Testing

- **Companion Unit Tests**:
  - `CompanionTest.kt` passes with unit pins for `resolveIconId`, height clamping, and persistence roundtrips.
- **Launcher Bundled Icons Test**:
  - `LauncherBundledIconsTest.kt` verifies that all 13 bundled icons load correctly and match the launcher registry.
- **Compilation**:
  - Full debug test suite passes cleanly with zero regressions.
