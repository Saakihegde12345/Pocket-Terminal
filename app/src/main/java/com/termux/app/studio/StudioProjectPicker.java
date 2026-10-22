/* SPDX-License-Identifier: GPL-3.0-only */
package com.termux.app.studio;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.ContextThemeWrapper;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.shared.termux.TermuxConstants;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small native directory picker. All directory I/O is off the UI thread. */
final class StudioProjectPicker {
    private static final String SAVED_PATHS = "project_directories_v1";
    private static final int MAX_SAVED = 30;
    private static final int MAX_VISIBLE_DIRECTORIES = 200;
    private static final long CWD_CHECK_DELAY_MS = 100L;
    private static final int MAX_CWD_CHECKS = 10;

    private final TermuxActivity activity;
    private final SharedPreferences preferences;
    private final StudioFileBrowser files;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final File home = TermuxConstants.TERMUX_HOME_DIR;
    private boolean closed;
    private int generation;
    private AlertDialog dialog;

    StudioProjectPicker(TermuxActivity activity, SharedPreferences preferences) {
        this.activity = activity;
        this.preferences = preferences;
        files = new StudioFileBrowser(activity, directory -> resolve(directory.getPath(), this::open));
    }

    private AlertDialog.Builder builder() {
        return new AlertDialog.Builder(new ContextThemeWrapper(activity,
            android.R.style.Theme_Material_Dialog_Alert));
    }

    private void show(AlertDialog next) {
        if (closed || activity.isFinishing() || activity.isDestroyed()) return;
        if (dialog != null) dialog.dismiss();
        dialog = next;
        dialog.show();
    }

    void showProjects() {
        final int request = ++generation;
        List<String> saved = new ArrayList<>(preferences.getStringSet(SAVED_PATHS, new HashSet<>()));
        java.util.Collections.sort(saved, String.CASE_INSENSITIVE_ORDER);
        List<String> labels = new ArrayList<>();
        labels.add(activity.getString(R.string.studio_browse_home));
        labels.add(activity.getString(R.string.studio_enter_path));
        for (String path : saved) labels.add(WorkspacePaths.safeLabel(WorkspacePaths.displayPath(path, home.getPath())));
        AlertDialog next = builder().setTitle(R.string.studio_projects)
            .setItems(labels.toArray(new String[0]), (d, which) -> {
                if (which == 0) browse(home.getPath());
                else if (which == 1) enterPath();
                else showDirectoryActions(new File(saved.get(which - 2)));
            })
            .setNegativeButton(android.R.string.cancel, null).create();
        next.setOnCancelListener(d -> { if (generation == request) generation++; });
        show(next);
    }

    private interface DirectoryAction { void accept(File directory); }

    private void resolve(String path, DirectoryAction action) {
        if (closed) return;
        final int request = ++generation;
        io.execute(() -> {
            try {
                File directory = WorkspacePaths.resolveDirectory(path, home);
                activity.runOnUiThread(() -> {
                    if (!closed && request == generation && !activity.isFinishing() && !activity.isDestroyed())
                        action.accept(directory);
                });
            } catch (IOException | SecurityException e) {
                activity.runOnUiThread(() -> {
                    if (!closed && request == generation)
                        activity.showToast(e.getMessage(), true);
                });
            }
        });
    }

    private void enterPath() {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setTextColor(StudioUi.TEXT);
        input.setHintTextColor(StudioUi.MUTED);
        input.setHint(R.string.studio_directory_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setTypeface(Typeface.MONOSPACE);
        input.setTextSize(14);
        LinearLayout wrap = new LinearLayout(activity);
        int inset = StudioUi.dp(activity, 20);
        wrap.setPadding(inset, 0, inset, 0);
        wrap.addView(input, new LinearLayout.LayoutParams(-1, -2));
        show(builder().setTitle(R.string.studio_enter_path).setView(wrap)
            .setPositiveButton(R.string.studio_browse, (d, which) -> resolve(input.getText().toString(), this::showDirectoryActions))
            .setNegativeButton(android.R.string.cancel, (d, which) -> showProjects()).create());
    }

    private void browse(String path) {
        resolve(path, directory -> {
            final int request = ++generation;
            // A cancelable loading dialog invalidates the request; late filesystem results cannot reopen it.
            AlertDialog loading = builder().setMessage(R.string.studio_loading_folders)
                .setNegativeButton(android.R.string.cancel, (d, which) -> generation++).create();
            loading.setOnCancelListener(d -> generation++);
            show(loading);
            io.execute(() -> {
                File[] children;
                try { children = directory.listFiles(File::isDirectory); }
                catch (SecurityException e) { children = null; }
                if (children != null) Arrays.sort(children, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
                final File[] result = children;
                activity.runOnUiThread(() -> {
                    if (closed || request != generation || activity.isFinishing() || activity.isDestroyed()) return;
                    if (result == null) {
                        activity.showToast(activity.getString(R.string.studio_unreadable_directory), true);
                        showProjects();
                        return;
                    }
                    List<String> labels = new ArrayList<>();
                    labels.add(activity.getString(R.string.studio_open_here));
                    labels.add(activity.getString(R.string.studio_parent_folder));
                    labels.add(activity.getString(R.string.studio_project_files));
                    int shown = Math.min(result.length, MAX_VISIBLE_DIRECTORIES);
                    for (int i = 0; i < shown; i++) labels.add("▸ " + WorkspacePaths.safeLabel(result[i].getName()));
                    String title = WorkspacePaths.safeLabel(WorkspacePaths.displayPath(directory.getPath(), home.getPath()));
                    if (shown < result.length) title += " · " + shown + "/" + result.length;
                    AlertDialog picker = builder().setTitle(title)
                        .setItems(labels.toArray(new String[0]), (d, which) -> {
                            if (which == 0) open(directory);
                            else if (which == 1) {
                                File parent = directory.getParentFile();
                                if (parent != null) browse(parent.getPath()); else browse(directory.getPath());
                            } else if (which == 2) files.show(directory);
                            else browse(result[which - 3].getPath());
                        })
                        .setNegativeButton(R.string.studio_projects, (d, which) -> showProjects()).create();
                    show(picker);
                });
            });
        });
    }

    private void showDirectoryActions(File directory) {
        String[] actions = {activity.getString(R.string.studio_open_here), activity.getString(R.string.studio_browse),
            activity.getString(R.string.studio_project_files),
            activity.getString(R.string.studio_forget_project)};
        show(builder().setTitle(WorkspacePaths.safeLabel(directory.getName())).setItems(actions, (d, which) -> {
            if (which == 0) resolve(directory.getPath(), this::open);
            else if (which == 1) browse(directory.getPath());
            else if (which == 2) files.show(directory);
            else {
                Set<String> paths = new HashSet<>(preferences.getStringSet(SAVED_PATHS, new HashSet<>()));
                paths.remove(directory.getPath());
                preferences.edit().putStringSet(SAVED_PATHS, paths).apply();
                showProjects();
            }
        }).setNegativeButton(android.R.string.cancel, null).create());
    }

    private void open(File requestedDirectory) {
        final File directory;
        try {
            // Revalidate after browsing: a directory can disappear before the user taps Open.
            directory = WorkspacePaths.resolveDirectory(requestedDirectory.getPath(), home);
        } catch (IOException | SecurityException e) {
            activity.showToast(e.getMessage(), true);
            return;
        }
        TermuxService service = activity.getTermuxService();
        // Require the normal bootstrap-created session before opening a project session.
        if (service == null || activity.getCurrentSession() == null || service.wantsToStop()) {
            activity.showToast(activity.getString(R.string.studio_wait_for_shell), true);
            return;
        }
        final int request = ++generation;
        int before = service.getTermuxSessionsSize();
        activity.getTermuxTerminalSessionClient().addNewSessionInDirectory(false, directory.getName(), directory.getPath());
        if (service.getTermuxSessionsSize() <= before) return;
        TerminalSession terminalSession = activity.getCurrentSession();
        if (terminalSession == null) return;
        // Keep the normal new-session flow immediate; bookmark persistence waits for the shell's actual cwd.
        activity.getTerminalView().requestFocus();
        TermuxSession selected = service.getTermuxSessionForTerminalSession(terminalSession);
        if (selected == null) return;
        activity.getTerminalView().postDelayed(() ->
            verifyActualCwd(directory, terminalSession, selected, request, 0), CWD_CHECK_DELAY_MS);
    }

    private void verifyActualCwd(File directory, TerminalSession terminalSession, TermuxSession selected,
                                 int request, int check) {
        TermuxService service = activity.getTermuxService();
        if (closed || request != generation || service == null || service.wantsToStop()
            || activity.isFinishing() || activity.isDestroyed()
            || activity.getCurrentSession() != terminalSession
            || service.getTermuxSessionForTerminalSession(terminalSession) != selected
            || !terminalSession.isRunning()) return;

        if (directory.getPath().equals(terminalSession.getCwd())) {
            saveBookmark(directory);
            return;
        }
        if (check + 1 < MAX_CWD_CHECKS) {
            activity.getTerminalView().postDelayed(() ->
                verifyActualCwd(directory, terminalSession, selected, request, check + 1), CWD_CHECK_DELAY_MS);
        } else if (!directory.isDirectory()) {
            // If the requested directory vanished during startup, do not leave a shell in an
            // inherited directory that looks like a successful project open.
            terminalSession.finishIfRunning();
            activity.showToast(activity.getString(R.string.studio_unreadable_directory), true);
        }
    }

    private void saveBookmark(File directory) {
        Set<String> paths = new HashSet<>(preferences.getStringSet(SAVED_PATHS, new HashSet<>()));
        if (paths.size() < MAX_SAVED || paths.contains(directory.getPath())) {
            paths.add(directory.getPath());
            preferences.edit().putStringSet(SAVED_PATHS, paths).apply();
        }
    }

    void close() {
        closed = true;
        files.close();
        generation++;
        if (dialog != null) dialog.dismiss();
        dialog = null;
        io.shutdownNow();
    }

    Object retainDraft() { return files.retainDraft(); }
    void restoreDraft(Object draft) { files.restoreDraft(draft); }
}
