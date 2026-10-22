/* SPDX-License-Identifier: GPL-3.0-only */
package com.termux.app.studio;

import android.app.AlertDialog;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.termux.TermuxConstants;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Search the installed APT catalog; changes use the normal interactive package tools. */
final class StudioPackages {
    private final TermuxActivity activity;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private AlertDialog dialog;
    private volatile Process process;
    private Future<?> job;
    private int generation;
    private boolean closed;
    private EditText search;
    private Runnable pending;
    StudioPackages(TermuxActivity activity) { this.activity = activity; }

    private void cancel() {
        generation++;
        if (search != null && pending != null) search.removeCallbacks(pending);
        if (job != null) job.cancel(true);
        Process running = process;
        if (running != null) running.destroy();
    }

    void showCatalog() {
        if (closed || activity.isFinishing() || activity.isDestroyed()) return;
        if (dialog != null) dialog.dismiss();
        LinearLayout body = StudioUi.column(activity);
        EditText query = StudioUi.input(activity, R.string.studio_find_package);
        search = query;
        query.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        body.addView(query);
        TextView status = StudioUi.text(activity, "", 12, StudioUi.MUTED);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        body.addView(status);
        ArrayAdapter<PackageEntry> adapter = new ArrayAdapter<PackageEntry>(activity,
                android.R.layout.simple_list_item_1, new ArrayList<>()) {
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                TextView text = (TextView) super.getView(position, recycled, parent);
                text.setTextColor(StudioUi.TEXT); text.setTextSize(14); text.setMinHeight(StudioUi.dp(activity, 56));
                text.setMaxLines(3); return text;
            }
        };
        ListView list = new ListView(activity); list.setAdapter(adapter);
        body.addView(list, new LinearLayout.LayoutParams(-1, StudioUi.dp(activity,
            Math.max(120, Math.min(340, activity.getResources().getConfiguration().screenHeightDp / 2)))));
        list.setOnItemClickListener((p, v, position, id) -> {
            PackageEntry entry = adapter.getItem(position);
            if (entry == null) return;
            new AlertDialog.Builder(activity).setTitle(entry.name).setMessage(entry.description
                + "\n\nThe package tool will show the download size and ask for confirmation in a new terminal.")
                .setPositiveButton("Install / update", (d, w) -> run("install", entry.name))
                .setNeutralButton("Uninstall", (d, w) -> new AlertDialog.Builder(activity)
                    .setTitle("Uninstall " + entry.name + "?").setMessage("Review dependencies in the package tool before confirming removal.")
                    .setPositiveButton("Continue", (a, b) -> run("uninstall", entry.name))
                    .setNegativeButton(android.R.string.cancel, null).show())
                .setNegativeButton(android.R.string.cancel, null).show();
        });
        dialog = new AlertDialog.Builder(activity).setTitle(R.string.studio_packages).setView(body)
            .setPositiveButton("Update index", (d, w) -> run("update", null))
            .setNeutralButton("Upgrade all", (d, w) -> new AlertDialog.Builder(activity).setTitle("Upgrade installed packages?")
                .setMessage("Review the proposed changes and confirm them in a new terminal.")
                .setPositiveButton("Continue", (a, b) -> run("upgrade", null))
                .setNegativeButton(android.R.string.cancel, null).show())
            .setNegativeButton(android.R.string.cancel, null).create();
        AlertDialog window = dialog;
        window.setOnDismissListener(d -> cancel());
        window.show(); StudioUi.styleDialog(activity, window);
        StudioUi.fitDialogContent(activity, window, list, 340, 230);
        pending = () -> {
            cancel();
            int request = generation;
            String term = query.getText().toString().trim();
            adapter.clear(); status.setText("Searching local package index…");
            job = worker.submit(() -> {
                List<PackageEntry> found = new ArrayList<>();
                String error = null;
                Process running = null;
                try {
                    File executable = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "apt-cache");
                    String expression = PackageEntry.searchExpression(term);
                    if (Thread.currentThread().isInterrupted()) return;
                    running = new ProcessBuilder(executable.getPath(), "search", "--names-only", "--", expression)
                        .redirectErrorStream(true).start();
                    process = running;
                    if (Thread.currentThread().isInterrupted()) { running.destroy(); return; }
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(running.getInputStream(), "UTF-8"))) {
                        String line;
                        int read = 0;
                        while ((line = reader.readLine()) != null && found.size() < 200 && read < 256 * 1024) {
                            if (Thread.currentThread().isInterrupted()) return;
                            read += line.length();
                            try { found.add(PackageEntry.parse(line)); } catch (IllegalArgumentException ignored) { }
                        }
                    }
                    if (found.size() < 200 && running.waitFor() != 0) error = "Package index unavailable. Bootstrap the shell, then choose Update index.";
                } catch (Exception e) { error = "Package index unavailable. Bootstrap the shell, then choose Update index."; }
                finally { if (running != null) running.destroy(); if (process == running) process = null; }
                final String failure = error;
                activity.runOnUiThread(() -> {
                    if (closed || request != generation || !window.isShowing()) return;
                    adapter.addAll(found);
                    status.setText(failure != null ? failure : found.isEmpty() ? "No matches. Update the index or try another name."
                        : found.size() + " packages" + (found.size() == 200 ? " · narrow your search to see more" : ""));
                });
            });
        };
        query.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                cancel(); query.postDelayed(pending, 200);
            }
            public void afterTextChanged(Editable s) { }
        });
        query.post(pending);
    }

    private void run(String action, String name) {
        if (dialog != null) dialog.dismiss();
        StudioCommands.start(activity, "pkg", name == null ? new String[]{action} : new String[]{action, name}, "Packages");
    }
    void close() { closed = true; cancel(); if (dialog != null) dialog.dismiss(); worker.shutdownNow(); }
}
