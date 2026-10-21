/* SPDX-License-Identifier: GPL-3.0-only */
package com.termux.app.studio;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.termux.R;
import com.termux.app.TermuxActivity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Native, read-only project browser. File contents are never sent to a shell or a network service. */
final class StudioFileBrowser {
    interface OpenShell { void open(File directory); }
    private final TermuxActivity activity;
    private final OpenShell openShell;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Dialog dialog;
    private boolean closed;

    StudioFileBrowser(TermuxActivity activity, OpenShell openShell) {
        this.activity = activity;
        this.openShell = openShell;
    }

    void show(File root) {
        if (closed || activity.isFinishing() || activity.isDestroyed()) return;
        if (dialog != null) dialog.dismiss();
        Panel panel = new Panel(root);
        dialog = panel.window;
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(-1, -1);
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
        panel.list(root);
    }

    private final class Panel {
        final File root;
        final Dialog window;
        final LinearLayout filters;
        final EditText query;
        final TextView path;
        final TextView status;
        final TextView up;
        final TextView hidden;
        final TextView copy;
        final TextView shell;
        final ListView list;
        final TextView source;
        final ScrollView preview;
        final ArrayAdapter<WorkspaceFiles.Entry> adapter;
        final Runnable filterTask;
        File directory;
        File selected;
        File previewTarget;
        String rootPath;
        Future<?> job;
        int generation;
        boolean active = true;
        boolean showHidden;
        boolean previewing;

        Panel(File root) {
            this.root = root;
            directory = root;
            rootPath = root.getPath();
            filterTask = () -> list(directory);
            window = new Dialog(new ContextThemeWrapper(activity, android.R.style.Theme_Material_NoActionBar));
            window.requestWindowFeature(Window.FEATURE_NO_TITLE);
            LinearLayout body = StudioUi.column(activity);
            LinearLayout heading = row();
            TextView title = StudioUi.text(activity, activity.getString(R.string.studio_project_files), 21, StudioUi.TEXT);
            heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            TextView close = button("×", R.string.studio_close_files);
            close.setTextSize(24);
            close.setOnClickListener(v -> window.dismiss());
            heading.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
            body.addView(heading);
            path = StudioUi.text(activity, "", 12, StudioUi.MUTED);
            path.setMaxLines(2);
            path.setTypeface(Typeface.MONOSPACE);
            path.setPadding(0, dp(8), 0, dp(8));
            body.addView(path);

            filters = row();
            query = StudioUi.input(activity, R.string.studio_filter_files);
            query.setFilters(new InputFilter[]{new InputFilter.LengthFilter(256)});
            filters.addView(query, new LinearLayout.LayoutParams(0, dp(56), 1));
            hidden = button(activity.getString(R.string.studio_hidden_off), R.string.studio_show_hidden_files);
            hidden.setOnClickListener(v -> {
                showHidden = !showHidden;
                hidden.setText(showHidden ? R.string.studio_hidden_on : R.string.studio_hidden_off);
                hidden.setSelected(showHidden);
                hidden.setTextColor(showHidden ? StudioUi.accent(activity) : StudioUi.TEXT);
                list(directory);
            });
            filters.addView(hidden);
            body.addView(filters);
            LinearLayout controls = row();
            up = button(activity.getString(R.string.studio_parent_folder), R.string.studio_parent_folder);
            up.setOnClickListener(v -> {
                if (previewing) list(directory);
                else if (directory.getParentFile() != null) list(directory.getParentFile());
            });
            controls.addView(up);
            TextView refresh = button(activity.getString(R.string.studio_refresh), R.string.studio_refresh_files);
            refresh.setOnClickListener(v -> { if (previewing) read(previewTarget); else list(directory); });
            controls.addView(refresh);
            copy = button(activity.getString(R.string.studio_copy_path), R.string.studio_copy_path);
            copy.setOnClickListener(v -> {
                if (selected == null) return;
                ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.studio_project_files), selected.getPath()));
                    activity.showToast(activity.getString(R.string.studio_path_copied), false);
                }
            });
            controls.addView(copy);
            shell = button(activity.getString(R.string.studio_open_here), R.string.studio_open_here);
            shell.setOnClickListener(v -> {
                if (selected == null) return;
                File target = previewing ? selected.getParentFile() : selected;
                window.dismiss();
                if (target != null && !closed) openShell.open(target);
            });
            controls.addView(shell);
            HorizontalScrollView toolbar = new HorizontalScrollView(activity);
            toolbar.setHorizontalScrollBarEnabled(true);
            toolbar.addView(controls);
            body.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(52)));

            status = StudioUi.text(activity, "", 12, StudioUi.MUTED);
            status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            status.setPadding(0, dp(8), 0, dp(8));
            body.addView(status);
            adapter = new ArrayAdapter<WorkspaceFiles.Entry>(activity, android.R.layout.simple_list_item_1, new ArrayList<>()) {
                @Override public View getView(int position, View recycled, ViewGroup parent) {
                    TextView view = (TextView) super.getView(position, recycled, parent);
                    WorkspaceFiles.Entry entry = getItem(position);
                    if (entry != null) {
                        view.setText((entry.directory ? "▸  " : "·  ") + WorkspacePaths.safeLabel(entry.file.getName()));
                        view.setContentDescription(activity.getString(entry.directory
                            ? R.string.studio_file_folder_label : R.string.studio_file_preview_label,
                            WorkspacePaths.safeLabel(entry.file.getName())));
                    }
                    view.setTextColor(entry != null && entry.directory ? StudioUi.accent(activity) : StudioUi.TEXT);
                    view.setTypeface(Typeface.MONOSPACE);
                    view.setTextSize(14);
                    view.setMinHeight(dp(52));
                    return view;
                }
            };
            list = new ListView(activity);
            list.setAdapter(adapter);
            list.setOnItemClickListener((parent, view, position, id) -> {
                WorkspaceFiles.Entry entry = adapter.getItem(position);
                if (entry != null) { if (entry.directory) list(entry.file); else read(entry.file); }
            });
            body.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
            source = StudioUi.text(activity, "", 13, StudioUi.TEXT);
            source.setTextIsSelectable(true);
            source.setTypeface(Typeface.MONOSPACE);
            source.setTextDirection(View.TEXT_DIRECTION_LTR);
            source.setGravity(Gravity.TOP | Gravity.START);
            source.setPadding(dp(12), dp(12), dp(12), dp(12));
            source.setBackgroundColor(StudioUi.BACKGROUND);
            preview = new ScrollView(activity);
            preview.addView(source, new ScrollView.LayoutParams(-1, -2));
            preview.setVisibility(View.GONE);
            body.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
            TextView note = StudioUi.text(activity, activity.getString(R.string.studio_files_note), 11, StudioUi.MUTED);
            note.setPadding(0, dp(8), 0, 0);
            body.addView(note);
            window.setContentView(body, new LinearLayout.LayoutParams(-1, -1));
            query.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    cancel();
                    adapter.clear();
                    copy.setEnabled(false);
                    shell.setEnabled(false);
                    status.setText(R.string.studio_loading_files);
                    if (active && !closed) query.postDelayed(filterTask, 180);
                }
                @Override public void afterTextChanged(Editable s) {}
            });
            window.setOnDismissListener(d -> {
                active = false;
                cancel();
                if (dialog == window) dialog = null;
            });
        }

        int dp(int value) { return StudioUi.dp(activity, value); }
        LinearLayout row() {
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            return row;
        }
        TextView button(String label, int description) {
            return StudioUi.button(activity, label, activity.getString(description), false);
        }
        void cancel() {
            generation++;
            query.removeCallbacks(filterTask);
            if (job != null) job.cancel(true);
        }
        int begin(File target, boolean file) {
            cancel();
            previewing = file;
            if (file) previewTarget = target;
            selected = null;
            copy.setEnabled(false);
            shell.setEnabled(false);
            up.setText(file ? R.string.studio_back_to_folder : R.string.studio_parent_folder);
            up.setContentDescription(activity.getString(file ? R.string.studio_back_to_folder : R.string.studio_parent_folder));
            up.setEnabled(file || !directory.getPath().equals(rootPath));
            filters.setVisibility(file ? View.GONE : View.VISIBLE);
            list.setVisibility(file ? View.GONE : View.VISIBLE);
            preview.setVisibility(file ? View.VISIBLE : View.GONE);
            source.setText("");
            adapter.clear();
            path.setText(WorkspacePaths.safeLabel(target.getPath()));
            status.setText(R.string.studio_loading_files);
            return generation;
        }
        boolean current(int request) {
            return active && !closed && generation == request && !activity.isFinishing() && !activity.isDestroyed();
        }
        void failed(int request, Exception error) {
            activity.runOnUiThread(() -> {
                if (current(request)) status.setText(error.getMessage() == null
                    ? activity.getString(R.string.studio_files_unreadable) : WorkspacePaths.safeLabel(error.getMessage()));
            });
        }
        void list(File target) {
            if (!active || closed) return;
            int request = begin(target, false);
            String term = query.getText().toString();
            boolean includeHidden = showHidden;
            job = worker.submit(() -> {
                try {
                    WorkspaceFiles.Listing result = WorkspaceFiles.list(root, target, includeHidden, term);
                    activity.runOnUiThread(() -> {
                        if (!current(request)) return;
                        directory = result.directory;
                        rootPath = result.root.getPath();
                        selected = directory;
                        path.setText(WorkspacePaths.safeLabel(directory.getPath()));
                        adapter.addAll(result.entries);
                        status.setText(activity.getString(R.string.studio_files_count, result.entries.size(), result.total));
                        // Canonical path comparison was already done by the worker.
                        up.setEnabled(!directory.getPath().equals(rootPath));
                        copy.setEnabled(true);
                        shell.setEnabled(true);
                    });
                } catch (IOException | SecurityException e) { failed(request, e); }
            });
        }
        void read(File target) {
            if (!active || closed) return;
            int request = begin(target, true);
            job = worker.submit(() -> {
                try {
                    WorkspaceFiles.Preview result = WorkspaceFiles.preview(root, target);
                    activity.runOnUiThread(() -> {
                        if (!current(request)) return;
                        selected = result.file;
                        path.setText(WorkspacePaths.safeLabel(result.file.getPath()));
                        source.setText(result.text);
                        preview.scrollTo(0, 0);
                        status.setText(activity.getString(R.string.studio_file_bytes, result.bytes));
                        copy.setEnabled(true);
                        shell.setEnabled(true);
                    });
                } catch (IOException | SecurityException e) { failed(request, e); }
            });
        }
    }

    void close() {
        closed = true;
        if (dialog != null) dialog.dismiss();
        dialog = null;
        worker.shutdownNow();
    }
}
