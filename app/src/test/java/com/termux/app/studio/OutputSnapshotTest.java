/* SPDX-License-Identifier: GPL-3.0-only */
package com.termux.app.studio;

import android.app.Application;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalOutput;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
public class OutputSnapshotTest {
    private static final TerminalOutput OUTPUT = new TerminalOutput() {
        @Override public void write(byte[] data, int offset, int count) {}
        @Override public void titleChanged(String oldTitle, String newTitle) {}
        @Override public void onCopyTextToClipboard(String text) {}
        @Override public void onPasteTextFromClipboard() {}
        @Override public void onBell() {}
        @Override public void onColorsChanged() {}
    };

    private TerminalEmulator terminal(int columns) {
        return new TerminalEmulator(OUTPUT, columns, 4, 10, 15, 10000, null);
    }

    private void append(TerminalEmulator terminal, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        terminal.append(bytes, bytes.length);
    }

    @Test public void wrappedOutputSearchesAsOneLineAndPreservesHardNewlines() {
        TerminalEmulator terminal = terminal(8);
        append(terminal, "abcdefghijklm\r\nNEXT");
        OutputSnapshot snapshot = OutputSnapshot.capture(terminal);
        assertTrue(snapshot.text.startsWith("abcdefghijklm\nNEXT"));
        assertEquals(1, OutputSearch.find(snapshot.text, "ghijk", true).matches.size());
        assertFalse(snapshot.limited);
    }

    @Test public void snapshotDoesNotMutateLiveBufferAndRemainsDetached() {
        TerminalEmulator terminal = terminal(30);
        append(terminal, "\u001b[31m日本語 👩‍💻\u001b[0m");
        String before = terminal.getScreen().getTranscriptText();
        OutputSnapshot snapshot = OutputSnapshot.capture(terminal);
        assertEquals(before, terminal.getScreen().getTranscriptText());
        assertTrue(snapshot.text.contains("日本語 👩‍💻"));
        assertFalse(snapshot.text.contains("\u001b"));
        append(terminal, " LATER");
        assertFalse(snapshot.text.contains("LATER"));
        assertTrue(OutputSnapshot.capture(terminal).text.contains("LATER"));
    }

    @Test public void rowLimitKeepsNewestOutputAndReportsTruncation() {
        TerminalEmulator terminal = terminal(30);
        for (int i = 0; i < 3000; i++) append(terminal, "row-" + i + "\r\n");
        OutputSnapshot snapshot = OutputSnapshot.capture(terminal);
        assertTrue(snapshot.limited);
        assertFalse(snapshot.text.contains("row-0\n"));
        assertTrue(snapshot.text.contains("row-2999"));
        assertTrue(snapshot.text.split("\n", -1).length <= OutputSnapshot.MAX_ROWS);
    }

    @Test public void characterLimitKeepsUnicodeBoundariesAndLatestText() {
        TerminalEmulator terminal = terminal(512);
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < 170; i++) line.append("😀x");
        for (int i = 0; i < 1000; i++) append(terminal, line + "\r\n");
        append(terminal, "END-MARKER");
        OutputSnapshot snapshot = OutputSnapshot.capture(terminal);
        assertTrue(snapshot.limited);
        assertTrue(snapshot.text.length() <= OutputSnapshot.MAX_CHARACTERS);
        assertFalse(Character.isLowSurrogate(snapshot.text.charAt(0)));
        assertTrue(snapshot.text.contains("END-MARKER"));
    }
}
