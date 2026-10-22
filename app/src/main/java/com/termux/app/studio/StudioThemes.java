/* SPDX-License-Identifier: GPL-3.0-only */
package com.termux.app.studio;

import android.app.AlertDialog;
import android.util.AtomicFile;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.termux.TermuxConstants;
import com.termux.terminal.TerminalColorScheme;
import com.termux.terminal.TextStyle;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class StudioThemes {
    private final TermuxActivity activity;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private AlertDialog dialog;
    private boolean closed;
    StudioThemes(TermuxActivity activity) { this.activity = activity; }

    void showEditor() {
        if (closed) return;
        worker.execute(() -> {
            Properties colors = TerminalTheme.preset(0);
            try {
                Properties existing = new Properties();
                File file = TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE;
                if (file.isFile() && file.length() <= 65536) try (FileInputStream input = new FileInputStream(file)) { existing.load(input); }
                if (!existing.isEmpty()) {
                    TerminalColorScheme scheme = new TerminalColorScheme();
                    scheme.updateWith(existing);
                    int[] special = {TextStyle.COLOR_INDEX_BACKGROUND, TextStyle.COLOR_INDEX_FOREGROUND, TextStyle.COLOR_INDEX_CURSOR};
                    for (int i = 0; i < TerminalTheme.KEYS.length; i++) {
                        int color = scheme.mDefaultColors[i < 3 ? special[i] : i - 3];
                        colors.setProperty(TerminalTheme.KEYS[i], String.format(Locale.ROOT, "#%06x", color & 0xffffff));
                    }
                }
            } catch (Exception ignored) { }
            activity.runOnUiThread(() -> { if (!closed && !activity.isFinishing() && !activity.isDestroyed()) edit(colors); });
        });
    }

    private void edit(Properties initial) {
        if (dialog != null) dialog.dismiss();
        LinearLayout body = StudioUi.column(activity);
        body.addView(StudioUi.text(activity, activity.getString(R.string.studio_theme_note), 13, StudioUi.MUTED));
        EditText[] inputs = new EditText[TerminalTheme.KEYS.length];
        android.widget.TextView preset = StudioUi.button(activity, "Choose preset", "Choose terminal color preset", false);
        body.addView(preset);
        for (int i = 0; i < inputs.length; i++) {
            body.addView(StudioUi.text(activity, TerminalTheme.KEYS[i], 13, StudioUi.MUTED));
            inputs[i] = StudioUi.input(activity, R.string.studio_color_hex);
            inputs[i].setText(initial.getProperty(TerminalTheme.KEYS[i]));
            body.addView(inputs[i]);
        }
        preset.setOnClickListener(v -> new AlertDialog.Builder(activity).setTitle("Terminal preset")
            .setItems(TerminalTheme.PRESETS, (d, which) -> {
                Properties colors = TerminalTheme.preset(which);
                for (int i = 0; i < inputs.length; i++) inputs[i].setText(colors.getProperty(TerminalTheme.KEYS[i]));
            }).show());
        ScrollView scroll = new ScrollView(activity); scroll.addView(body);
        dialog = new AlertDialog.Builder(activity).setTitle(R.string.studio_terminal_theme).setView(scroll)
            .setPositiveButton(R.string.studio_apply_appearance, null).setNegativeButton(android.R.string.cancel, null).create();
        AlertDialog window = dialog;
        window.show(); StudioUi.styleDialog(activity, window);
        window.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Properties values = new Properties();
            for (int i = 0; i < inputs.length; i++) values.setProperty(TerminalTheme.KEYS[i], inputs[i].getText().toString());
            final Properties colors;
            try { colors = TerminalTheme.validate(values); }
            catch (IllegalArgumentException e) { activity.showToast(e.getMessage(), true); return; }
            window.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            worker.execute(() -> {
                String failure = null;
                AtomicFile atomic = new AtomicFile(TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE);
                FileOutputStream output = null;
                try {
                    File directory = TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE.getParentFile();
                    if (!directory.isDirectory() && !directory.mkdirs()) throw new java.io.IOException("Cannot create terminal configuration folder.");
                    output = atomic.startWrite();
                    colors.store(output, "Pocket-Terminal color scheme");
                    atomic.finishWrite(output); output = null;
                } catch (Exception e) { if (output != null) atomic.failWrite(output); failure = e.getMessage() == null ? "Unable to save terminal theme." : e.getMessage(); }
                final String error = failure;
                activity.runOnUiThread(() -> {
                    if (closed || activity.isFinishing() || activity.isDestroyed()) return;
                    if (error != null) { activity.showToast(error, true); window.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); }
                    else { window.dismiss(); activity.getTermuxTerminalSessionClient().checkForFontAndColors(); }
                });
            });
        });
    }
    void close() { closed = true; if (dialog != null) dialog.dismiss(); worker.shutdownNow(); }
}
