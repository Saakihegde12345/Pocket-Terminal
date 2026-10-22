/* SPDX-License-Identifier: GPL-3.0-only */
package com.termux.app.studio;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.system.Os;
import android.text.InputType;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.termux.R;
import com.termux.app.TermuxActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;

/** Explicit saves replace a file atomically, preserving its permissions and UTF-8 BOM. */
final class StudioFileEditor {
    private final TermuxActivity activity;
    private final ExecutorService worker;
    private AlertDialog dialog;
    private boolean closed;
    private Draft draft;
    private EditText editing;

    private static final class Draft {
        final File root;
        final WorkspaceFiles.Preview original;
        String text;
        Draft(File root, WorkspaceFiles.Preview original, String text) {
            this.root = root; this.original = original; this.text = text;
        }
    }

    StudioFileEditor(TermuxActivity activity, ExecutorService worker) {
        this.activity = activity; this.worker = worker;
    }

    void show(File root, WorkspaceFiles.Preview original, Runnable saved) {
        if (closed) return;
        if (dialog != null && dialog.isShowing()) return;
        EditText input = new EditText(activity);
        input.setTextColor(StudioUi.TEXT);
        input.setBackgroundColor(StudioUi.BACKGROUND);
        input.setTypeface(Typeface.MONOSPACE);
        input.setTextSize(14);
        input.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHorizontallyScrolling(true);
        input.setText(draft != null && draft.original == original ? draft.text : original.text);
        draft = new Draft(root, original, input.getText().toString());
        editing = input;
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(input, new ScrollView.LayoutParams(-1, -2));
        LinearLayout body = StudioUi.column(activity);
        body.addView(StudioUi.text(activity, WorkspacePaths.safeLabel(original.file.getName()), 15, StudioUi.MUTED));
        body.addView(scroll, new LinearLayout.LayoutParams(-1,
            StudioUi.dp(activity, Math.max(120, activity.getResources().getConfiguration().screenHeightDp / 2))));
        dialog = new AlertDialog.Builder(activity).setTitle(R.string.studio_edit_file).setView(body)
            .setPositiveButton(R.string.studio_save_snippet, null)
            .setNegativeButton(android.R.string.cancel, null).create();
        AlertDialog window = dialog;
        window.setOnDismissListener(d -> { if (!closed) { draft = null; editing = null; } });
        window.setCanceledOnTouchOutside(false);
        window.show();
        StudioUi.styleDialog(activity, window);
        StudioUi.fitDialogContent(activity, window, scroll, 400, 200);
        Runnable dismiss = () -> {
            if (input.getText().toString().equals(original.text)) window.dismiss();
            else new AlertDialog.Builder(activity).setTitle(R.string.studio_discard_edits)
                .setMessage(R.string.studio_discard_edits_question)
                .setPositiveButton(R.string.studio_discard_edits, (d, w) -> window.dismiss())
                .setNegativeButton(android.R.string.cancel, null).show();
        };
        window.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> dismiss.run());
        window.setOnKeyListener((d, key, event) -> {
            if (key != KeyEvent.KEYCODE_BACK) return false;
            if (event.getAction() == KeyEvent.ACTION_UP) dismiss.run();
            return true;
        });
        window.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = input.getText().toString();
            window.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            window.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
            input.setEnabled(false);
            worker.execute(() -> {
                File temporary = null;
                String error = null;
                try {
                    byte[] bytes = WorkspaceFiles.prepareSave(root, original, text);
                    File file = WorkspaceFiles.resolve(root, original.file);
                    int mode = Os.stat(file.getPath()).st_mode & 0777;
                    temporary = File.createTempFile(".pocket-edit-", ".tmp", file.getParentFile());
                    Os.chmod(temporary.getPath(), mode);
                    try (FileOutputStream output = new FileOutputStream(temporary)) {
                        output.write(bytes);
                        output.getFD().sync();
                    }
                    WorkspaceFiles.prepareSave(root, original, text);
                    Os.rename(temporary.getPath(), file.getPath());
                } catch (Exception e) { error = e.getMessage() == null ? "Unable to save this file." : e.getMessage(); }
                finally { if (temporary != null) temporary.delete(); }
                final String failure = error;
                activity.runOnUiThread(() -> {
                    if (closed || activity.isFinishing() || activity.isDestroyed() || !window.isShowing()) return;
                    if (failure != null) {
                        input.setEnabled(true);
                        window.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        window.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                        activity.showToast(failure, true);
                    } else {
                        window.dismiss();
                        saved.run();
                    }
                });
            });
        });
    }

    Object retainDraft() {
        if (draft == null || editing == null || editing.getText().toString().equals(draft.original.text)) return null;
        draft.text = editing.getText().toString();
        return draft;
    }

    void restoreDraft(Object saved) {
        if (!(saved instanceof Draft) || closed) return;
        draft = (Draft) saved;
        show(draft.root, draft.original, () -> activity.showToast("File saved.", false));
    }

    void close() { closed = true; if (dialog != null) dialog.dismiss(); }
}
