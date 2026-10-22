/* SPDX-License-Identifier: GPL-3.0-only */
package com.termux.app.studio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Storage Access Framework documents; exported metadata never contains authentication secrets. */
final class StudioWorkspace {
    private static final int EXPORT = 9101, IMPORT = 9102;
    private static final String BACKUP = "workspace_before_import_v1";
    private final TermuxActivity activity;
    private final SharedPreferences preferences;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean closed;
    StudioWorkspace(TermuxActivity activity, SharedPreferences preferences) { this.activity = activity; this.preferences = preferences; }

    void showOptions() {
        new AlertDialog.Builder(activity).setTitle(R.string.studio_workspace)
            .setItems(new String[]{"Export workspace", "Import workspace", "Restore saved shell layout", "Undo last import"}, (d, which) -> {
                try {
                    if (which == 0) activity.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE, "pocket-terminal-workspace.json"), EXPORT);
                    else if (which == 1) activity.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), IMPORT);
                    else if (which == 2) restore();
                    else {
                        String previous = preferences.getString(BACKUP, null);
                        if (previous == null) activity.showToast("There is no previous import to undo.", false);
                        else confirm(WorkspaceState.decode(previous), false);
                    }
                } catch (Exception e) { activity.showToast(message(e), true); }
            }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private WorkspaceState snapshot() throws Exception {
        JSONArray sessions = new JSONArray();
        TermuxService service = activity.getTermuxService();
        if (service != null) for (TermuxSession entry : service.getTermuxSessions()) {
            TerminalSession session = entry.getTerminalSession();
            if (session == null || !session.isRunning()) continue;
            String path = session.getCwd();
            if (path == null) continue;
            String name = WorkspacePaths.safeLabel(session.mSessionName == null ? "Shell" : session.mSessionName);
            sessions.put(new JSONObject().put("name", name.substring(0, Math.min(60, name.length()))).put("directory", path));
        }
        if (sessions.length() == 0) sessions = new JSONArray(preferences.getString(WorkspaceState.LAYOUT_KEY, "[]"));
        return WorkspaceState.capture(preferences, activity.getPreferences().getFontSize(), sessions);
    }

    boolean onActivityResult(int request, int result, Intent data) {
        if (request != EXPORT && request != IMPORT) return false;
        if (result != Activity.RESULT_OK || data == null || data.getData() == null || closed) return true;
        Uri uri = data.getData();
        final String export;
        try { export = request == EXPORT ? snapshot().json.toString(2) : null; }
        catch (Exception e) { activity.showToast(message(e), true); return true; }
        worker.execute(() -> {
            try {
                if (request == EXPORT) {
                    try (OutputStream output = activity.getContentResolver().openOutputStream(uri, "wt")) {
                        if (output == null) throw new java.io.IOException("Unable to open workspace document.");
                        output.write(export.getBytes(StandardCharsets.UTF_8));
                    }
                    post(() -> activity.showToast("Workspace exported. It contains saved directories, hosts and command snippets.", true));
                } else {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    try (InputStream input = activity.getContentResolver().openInputStream(uri)) {
                        if (input == null) throw new java.io.IOException("Unable to open workspace document.");
                        byte[] buffer = new byte[4096]; int count;
                        while ((count = input.read(buffer)) != -1) {
                            if (bytes.size() + count > WorkspaceState.MAX_BYTES) throw new java.io.IOException("Workspace file is larger than 2 MiB.");
                            bytes.write(buffer, 0, count);
                        }
                    }
                    String json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
                    WorkspaceState state = WorkspaceState.decode(json);
                    post(() -> confirm(state, true));
                }
            } catch (Exception e) { post(() -> activity.showToast(message(e), true)); }
        });
        return true;
    }

    private void confirm(WorkspaceState state, boolean backup) {
        try {
            new AlertDialog.Builder(activity).setTitle(backup ? "Import workspace?" : "Restore settings before last import?")
                .setMessage(state.json.getJSONArray("projects").length() + " projects · "
                    + state.json.getJSONArray("snippets").length() + " snippets · " + state.json.getJSONArray("hosts").length()
                    + " hosts\n\nReplaces saved workspace preferences. Your current terminal processes keep running. Use Restore saved shell layout separately to open saved directories.")
                .setPositiveButton("Apply", (d, which) -> {
                    try {
                        if (backup && !preferences.edit().putString(BACKUP, snapshot().json.toString()).commit())
                            throw new java.io.IOException("Unable to preserve current settings.");
                        state.apply(preferences);
                        activity.getPreferences().setFontSize(state.json.getInt("font"));
                        if (!backup) preferences.edit().remove(BACKUP).apply();
                        activity.recreate();
                    } catch (Exception e) { activity.showToast(message(e), true); }
                }).setNegativeButton(android.R.string.cancel, null).show();
        } catch (Exception e) { activity.showToast(message(e), true); }
    }

    private void restore() throws Exception {
        JSONArray sessions = new JSONArray(preferences.getString(WorkspaceState.LAYOUT_KEY, "[]"));
        if (sessions.length() == 0) { activity.showToast("Import an exported workspace with a shell layout first.", true); return; }
        new AlertDialog.Builder(activity).setTitle("Open saved shell directories?")
            .setMessage("Opens " + sessions.length() + " fresh shells. Running commands and remote connections are not restored. Existing sessions stay open; the eight-session limit still applies.")
            .setPositiveButton("Open shells", (d, which) -> worker.execute(() -> {
                List<File> directories = new ArrayList<>();
                List<String> names = new ArrayList<>();
                try {
                    for (int i = 0; i < sessions.length(); i++) {
                        JSONObject session = sessions.getJSONObject(i);
                        directories.add(WorkspacePaths.resolveDirectory(session.getString("directory"), TermuxConstants.TERMUX_HOME_DIR));
                        names.add(session.getString("name"));
                    }
                    post(() -> {
                        TermuxService service = activity.getTermuxService();
                        if (service == null || service.wantsToStop() || activity.getCurrentSession() == null) {
                            activity.showToast(activity.getString(R.string.studio_wait_for_shell), true); return;
                        }
                        if (service.getTermuxSessionsSize() + directories.size() > 8) {
                            activity.showToast("Close some sessions before restoring this layout. The limit is eight.", true); return;
                        }
                        for (int i = 0; i < directories.size(); i++) activity.getTermuxTerminalSessionClient()
                            .addNewSessionInDirectory(false, names.get(i), directories.get(i).getPath());
                    });
                } catch (Exception e) { post(() -> activity.showToast(message(e), true)); }
            })).setNegativeButton(android.R.string.cancel, null).show();
    }

    private static String message(Exception e) { return e.getMessage() == null ? "Unable to read or write workspace." : WorkspacePaths.safeLabel(e.getMessage()); }
    private void post(Runnable action) { activity.runOnUiThread(() -> { if (!closed && !activity.isFinishing() && !activity.isDestroyed()) action.run(); }); }
    void close() { closed = true; worker.shutdownNow(); }
}
