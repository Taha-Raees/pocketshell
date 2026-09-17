package com.termux.view;

import android.view.MotionEvent;

import com.termux.terminal.TerminalEmulator;

import java.util.ArrayList;
import java.util.List;

/**
 * PocketShell (owner iteration "terminal links → Companion"): maps a tap on
 * the terminal canvas to the URL under it, if any.
 *
 * TEXT ACCESS LIVES HERE, in the view module, on purpose: the app layer is
 * under a no-screen-scraping boundary (P7 audit — rendered terminal text is
 * never read by app sources), while THIS module already owns sanctioned
 * text access for selection and copy. Like selection, the probe reads the
 * buffer only at the moment of a USER tap (never in the background), hands
 * the extracted row segments to the CALLER-SUPPLIED matcher, and returns
 * only the match result. The URL policy itself stays in the app layer
 * (unit-pinned there); this class is pure extraction.
 *
 * Read-only over the emulator: nothing here writes the buffer, touches the
 * renderer, or affects the PTY, and the gesture flow is untouched — the
 * probe runs inside the already-confirmed single-tap callback.
 */
public final class TerminalLinkProbe {

    private TerminalLinkProbe() {}

    /** The URL policy: does a tap at (segment, column) land on a URL? */
    public interface UrlMatcher {
        /**
         * @param segments           the tapped row's text plus up to two
         *                           wrapped continuation rows, in order
         * @param columnsPerSegment  the terminal's column count (a full
         *                           segment spans exactly this many columns)
         * @param segment            which segment the tap landed in (0-based)
         * @param column             the tapped column within that segment
         * @return the matched URL, or null when the tap is not on one
         */
        String find(List<String> segments, int columnsPerSegment, int segment, int column);
    }

    /** The URL under the tap, or null (the tap keeps its previous meaning). */
    public static String urlAt(TerminalView view, MotionEvent event, UrlMatcher matcher) {
        TerminalEmulator emulator = view.mEmulator;
        if (emulator == null || matcher == null) return null;
        // Full-screen apps (vim, htop) own every tap while they report
        // mouse events — never steal one.
        if (emulator.isMouseTrackingActive()) return null;
        try {
            int[] cell = view.getColumnAndRow(event, true);
            int column = cell[0];
            int row = cell[1];
            final int tappedRow = row;
            com.termux.terminal.TerminalBuffer buffer = emulator.getScreen();
            int columns = emulator.mColumns;
            List<String> segments = new ArrayList<>(3);
            String text = buffer.getSelectedText(0, row, columns, row);
            if (text == null) return null;
            segments.add(text);
            // A wrapped line continues on the next buffer row; pull up to two
            // continuation segments so a boundary-split URL matches whole.
            while (buffer.getLineWrap(row) && segments.size() < 3) {
                row += 1;
                text = buffer.getSelectedText(0, row, columns, row);
                if (text == null) break;
                segments.add(text);
            }
            String hit = matcher.find(segments, columns, 0, column);
            if (hit != null) return hit;
            // Tap on a wrapped continuation row: the URL head sits above.
            if (tappedRow > 0 && buffer.getLineWrap(tappedRow - 1)) {
                String head = buffer.getSelectedText(0, tappedRow - 1, columns, tappedRow - 1);
                String tail = buffer.getSelectedText(0, tappedRow, columns, tappedRow);
                if (head != null && tail != null) {
                    List<String> two = new ArrayList<>(2);
                    two.add(head);
                    two.add(tail);
                    return matcher.find(two, columns, 1, column);
                }
            }
            return null;
        } catch (Throwable t) {
            // Any probe failure degrades to "no link" — never a crash.
            return null;
        }
    }
}
