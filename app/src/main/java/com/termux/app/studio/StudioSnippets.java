/* SPDX-License-Identifier: GPL-3.0-only */
package com.termux.app.studio;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.terminal.TerminalSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Explicitly saved command templates, with preview before insertion and no automatic Enter. */
final class StudioSnippets {
    private final TermuxActivity activity;
    private final SnippetStore store;
    private AlertDialog dialog;
    private boolean closed;

    StudioSnippets(TermuxActivity activity, SharedPreferences preferences) {
        this.activity = activity;
        store = new SnippetStore(preferences);
    }

    private AlertDialog.Builder builder() {
        return new AlertDialog.Builder(new ContextThemeWrapper(activity, android.R.style.Theme_Material_Dialog_Alert));
    }

    private boolean show(AlertDialog next) {
        if (closed || activity.isFinishing() || activity.isDestroyed()) return false;
        if (dialog != null) dialog.dismiss();
        dialog = next;
        next.show();
        StudioUi.styleDialog(activity, next);
        return true;
    }

    void showLibrary() {
        if (closed) return;
        List<CommandSnippet> all = store.load();
        LinearLayout body = StudioUi.column(activity);
        EditText search = StudioUi.input(activity, R.string.studio_find_snippet);
        body.addView(search, new LinearLayout.LayoutParams(-1, -2));
        TextView empty = StudioUi.text(activity, activity.getString(R.string.studio_no_snippets), 14, StudioUi.MUTED);
        empty.setPadding(0, dp(20), 0, dp(20));
        body.addView(empty);
        ArrayAdapter<CommandSnippet> adapter = new ArrayAdapter<CommandSnippet>(activity,
                android.R.layout.simple_list_item_1, new ArrayList<>(all)) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout row = StudioUi.column(activity);
                row.setPadding(dp(6), dp(10), dp(6), dp(10));
                CommandSnippet snippet = getItem(position);
                if (snippet != null) {
                    row.addView(StudioUi.text(activity, WorkspacePaths.safeLabel(snippet.name), 15, StudioUi.TEXT));
                    TextView command = StudioUi.text(activity, WorkspacePaths.safeLabel(snippet.command), 12, StudioUi.MUTED);
                    command.setTypeface(Typeface.MONOSPACE);
                    command.setMaxLines(2);
                    row.addView(command);
                }
                row.setMinimumHeight(dp(64));
                return row;
            }
        };
        ListView list = new ListView(activity);
        list.setAdapter(adapter);
        list.setEmptyView(empty);
        int height = Math.max(96, Math.min(300, activity.getResources().getConfiguration().screenHeightDp / 3));
        body.addView(list, new LinearLayout.LayoutParams(-1, dp(height)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().toLowerCase(Locale.ROOT);
                adapter.setNotifyOnChange(false);
                adapter.clear();
                for (CommandSnippet item : all) {
                    if (item.name.toLowerCase(Locale.ROOT).contains(query)
                        || item.command.toLowerCase(Locale.ROOT).contains(query)) adapter.add(item);
                }
                adapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            CommandSnippet item = adapter.getItem(position);
            if (item != null) preview(item);
        });
        show(builder().setTitle(R.string.studio_snippets).setView(body)
            .setPositiveButton(R.string.studio_new_snippet, (d, which) -> edit(null))
            .setNegativeButton(android.R.string.cancel, null).create());
    }

    private void preview(CommandSnippet snippet) {
        TerminalSession target = activity.getCurrentSession();
        String sessionName = target == null ? "" : target.mSessionName;
        if (sessionName == null || sessionName.isEmpty()) sessionName = target == null ? "" : target.getTitle();
        if (sessionName == null || sessionName.isEmpty()) sessionName = activity.getString(R.string.studio_current_session);
        LinearLayout body = StudioUi.column(activity);
        TextView hint = StudioUi.text(activity, activity.getString(R.string.studio_snippet_insert_note,
            WorkspacePaths.safeLabel(sessionName)), 13, StudioUi.MUTED);
        body.addView(hint);
        TextView command = StudioUi.text(activity, snippet.command, 14, StudioUi.TEXT);
        command.setTypeface(Typeface.MONOSPACE);
        command.setTextIsSelectable(true);
        command.setPadding(dp(10), dp(16), dp(10), dp(16));
        android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
        scroll.addView(command);
        body.addView(scroll, new LinearLayout.LayoutParams(-1,
            dp(Math.max(80, Math.min(180, activity.getResources().getConfiguration().screenHeightDp / 4)))));
        AlertDialog preview = builder().setTitle(WorkspacePaths.safeLabel(snippet.name)).setView(body)
            .setPositiveButton(R.string.studio_insert_snippet, null)
            .setNeutralButton(R.string.studio_edit_snippet, (d, which) -> edit(snippet))
            .setNegativeButton(android.R.string.cancel, (d, which) -> showLibrary()).create();
        if (!show(preview)) return;
        preview.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(target != null && target.isRunning() && target.getEmulator() != null);
        preview.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            // Never redirect a stale preview to a different session or write to a finished PTY.
            if (target == null || target != activity.getCurrentSession() || !target.isRunning() || target.getEmulator() == null) {
                activity.showToast(activity.getString(R.string.studio_session_changed), true);
                return;
            }
            if (!CommandSnippet.isCommandValid(snippet.command)) return;
            target.getEmulator().paste(snippet.command);
            preview.dismiss();
            activity.getTerminalView().requestFocus();
        });
    }

    private void edit(CommandSnippet existing) {
        LinearLayout body = StudioUi.column(activity);
        EditText name = StudioUi.input(activity, R.string.studio_snippet_name);
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(CommandSnippet.MAX_NAME_LENGTH)});
        EditText command = StudioUi.input(activity, R.string.studio_snippet_command);
        command.setTypeface(Typeface.MONOSPACE);
        // Validate the exact pasted text on Save. Truncation or flattening can change shell meaning.
        command.setSingleLine(false);
        command.setMinLines(2);
        command.setMaxLines(5);
        if (existing != null) {
            name.setText(existing.name);
            command.setText(existing.command);
        }
        body.addView(name);
        body.addView(command);
        TextView note = StudioUi.text(activity, activity.getString(R.string.studio_snippet_local_note), 12, StudioUi.MUTED);
        note.setPadding(0, dp(10), 0, 0);
        body.addView(note);
        AlertDialog editor = builder().setTitle(existing == null ? R.string.studio_new_snippet : R.string.studio_edit_snippet)
            .setView(body).setPositiveButton(R.string.studio_save_snippet, null)
            .setNegativeButton(android.R.string.cancel, (d, which) -> showLibrary())
            .setNeutralButton(existing == null ? android.R.string.cancel : R.string.studio_delete_snippet,
                existing == null ? null : (d, which) -> confirmDelete(existing)).create();
        if (!show(editor)) return;
        if (existing == null) editor.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(View.GONE);
        editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!CommandSnippet.isNameValid(name.getText().toString())) {
                name.setError(activity.getString(R.string.studio_invalid_snippet_name));
                return;
            }
            if (!CommandSnippet.isCommandValid(command.getText().toString())) {
                command.setError(activity.getString(R.string.studio_invalid_snippet_command));
                return;
            }
            CommandSnippet item = new CommandSnippet(existing == null ? UUID.randomUUID().toString() : existing.id,
                name.getText().toString(), command.getText().toString());
            if (!store.save(item)) {
                activity.showToast(activity.getString(R.string.studio_snippet_limit), true);
                return;
            }
            showLibrary();
        });
    }

    private void confirmDelete(CommandSnippet snippet) {
        show(builder().setTitle(R.string.studio_delete_snippet)
            .setMessage(activity.getString(R.string.studio_delete_snippet_question, WorkspacePaths.safeLabel(snippet.name)))
            .setPositiveButton(R.string.studio_delete_snippet, (d, which) -> { store.remove(snippet.id); showLibrary(); })
            .setNegativeButton(android.R.string.cancel, (d, which) -> edit(snippet)).create());
    }

    private int dp(int value) { return StudioUi.dp(activity, value); }

    void close() {
        closed = true;
        if (dialog != null) dialog.dismiss();
        dialog = null;
    }
}
