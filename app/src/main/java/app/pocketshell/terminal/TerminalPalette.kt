package app.pocketshell.terminal

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import app.pocketshell.R
import com.termux.terminal.TerminalColors
import com.termux.terminal.TextStyle

/**
 * Phase 3.1 Midnight Sapphire terminal palette (docs/PHASE-3.1-DESIGN.md §1.2).
 *
 * The defaults are written into the vendored terminal's static scheme at
 * process start — [com.termux.terminal.TerminalEmulator] copies
 * `TerminalColors.COLOR_SCHEME.mDefaultColors` into each new emulator, so
 * every session (existing future ones too) inherits them. This is the same
 * override path a real terminal theme engine uses; OSC sequences from running
 * programs still win (upstream `mCurrentColors` is never touched here), so
 * the terminal stays a real terminal, not a painted mockup.
 */
object TerminalPalette {

    fun applyDefaults(light: Boolean = false) {
        val c = TerminalColors.COLOR_SCHEME.mDefaultColors
        if (light) {
            c[TextStyle.COLOR_INDEX_FOREGROUND] = 0xFF17233B.toInt()
            c[TextStyle.COLOR_INDEX_BACKGROUND] = 0xFFF7F9FC.toInt()
            c[TextStyle.COLOR_INDEX_CURSOR] = 0xFF3D5A96.toInt()

            c[0] = 0xFFCBD5E1.toInt() // black
            c[1] = 0xFFB3384E.toInt() // red
            c[2] = 0xFF3E8F52.toInt() // green
            c[3] = 0xFF9E7B38.toInt() // yellow
            c[4] = 0xFF3D5A96.toInt() // blue
            c[5] = 0xFF8E5A9E.toInt() // magenta
            c[6] = 0xFF388B98.toInt() // cyan
            c[7] = 0xFF17233B.toInt() // white

            c[8] = 0xFF94A3B8.toInt() // bright black
            c[9] = 0xFFCC4D63.toInt() // bright red
            c[10] = 0xFF4EAA65.toInt() // bright green
            c[11] = 0xFFB59048.toInt() // bright yellow
            c[12] = 0xFF5274B8.toInt() // bright blue
            c[13] = 0xFFA56FB5.toInt() // bright magenta
            c[14] = 0xFF4DA3B2.toInt() // bright cyan
            c[15] = 0xFF0B1424.toInt() // bright white
        } else {
            c[TextStyle.COLOR_INDEX_FOREGROUND] = 0xFFD7E3F7.toInt()
            c[TextStyle.COLOR_INDEX_BACKGROUND] = 0xFF080F1D.toInt()
            c[TextStyle.COLOR_INDEX_CURSOR] = 0xFF7FA3EF.toInt()

            c[0] = 0xFF182238.toInt() // black
            c[1] = 0xFFDA6C7D.toInt() // red
            c[2] = 0xFF5FB572.toInt() // green
            c[3] = 0xFFC9A45C.toInt() // yellow
            c[4] = 0xFF6C9BD8.toInt() // blue
            c[5] = 0xFFB57FC6.toInt() // magenta
            c[6] = 0xFF56B3C2.toInt() // cyan
            c[7] = 0xFFC2D0E8.toInt() // white

            c[8] = 0xFF4E5F82.toInt() // bright black
            c[9] = 0xFFEE7D90.toInt() // bright red
            c[10] = 0xFF7BCB8C.toInt() // bright green
            c[11] = 0xFFE2BB6E.toInt() // bright yellow
            c[12] = 0xFF8AB4F0.toInt() // bright blue
            c[13] = 0xFFC996D8.toInt() // bright magenta
            c[14] = 0xFF74CFDE.toInt() // bright cyan
            c[15] = 0xFFE6EEFA.toInt() // bright white
        }
    }

    /**
     * JetBrains Mono NL (no-ligature build) — Regular/Bold/Italic, packaged in
     * res/font (OFL 1.1, docs/THIRD_PARTY.md). Applied via the vendored
     * TerminalView#setTypeface, which rebuilds the renderer and re-layouts
     * (PTY TIOCSWINSZ follows through upstream onSizeChanged).
     */
    fun typeface(context: Context): Typeface? = cache ?: ResourcesCompat.getFont(
        context.applicationContext,
        R.font.jetbrains_mono_nl_regular,
    )?.also { cache = it }

    @Volatile
    private var cache: Typeface? = null
}
