/* SPDX-License-Identifier: GPL-3.0-only */
package com.termux.app.studio;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;

import java.util.ArrayList;
import java.util.List;

/** Copy on the emulator's UI thread, then search the detached string on a worker. */
final class OutputSnapshot {
    static final int MAX_ROWS = 2000;
    static final int MAX_CHARACTERS = 200000;
    final String text;
    final boolean limited;

    private OutputSnapshot(String text, boolean limited) { this.text = text; this.limited = limited; }

    static OutputSnapshot capture(TerminalEmulator emulator) {
        TerminalBuffer screen = emulator.getScreen();
        int first = -screen.getActiveTranscriptRows();
        int row = emulator.mRows - 1;
        List<String> reversed = new ArrayList<>();
        int characters = 0;
        while (row >= first && reversed.size() < MAX_ROWS && characters < MAX_CHARACTERS) {
            // getSelectedText preserves trailing spaces on soft-wrapped rows.
            String line = screen.getSelectedText(0, row, emulator.mColumns, row);
            if (row < emulator.mRows - 1 && !screen.getLineWrap(row)) line += "\n";
            reversed.add(line);
            characters += line.length();
            row--;
        }
        StringBuilder text = new StringBuilder(characters);
        for (int i = reversed.size() - 1; i >= 0; i--) text.append(reversed.get(i));
        boolean limited = row >= first || text.length() > MAX_CHARACTERS;
        if (text.length() > MAX_CHARACTERS) {
            int start = text.length() - MAX_CHARACTERS;
            if (Character.isLowSurrogate(text.charAt(start))) start++;
            text.delete(0, start);
        }
        return new OutputSnapshot(text.toString(), limited);
    }
}
