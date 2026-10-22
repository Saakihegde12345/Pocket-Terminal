/* SPDX-License-Identifier: GPL-3.0-only */
package com.termux.app.studio;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

/** Versioned, whitelisted metadata. Importing a workspace never starts processes. */
final class WorkspaceState {
    static final int MAX_BYTES = 2 * 1024 * 1024;
    static final String LAYOUT_KEY = "saved_session_layout_v1";
    final JSONObject json;
    private WorkspaceState(JSONObject json) { this.json = json; }

    static WorkspaceState capture(SharedPreferences preferences, int font, JSONArray sessions) throws JSONException {
        JSONArray snippets = new JSONArray();
        for (CommandSnippet snippet : new SnippetStore(preferences).load())
            snippets.put(new JSONObject().put("id", snippet.id).put("name", snippet.name).put("command", snippet.command));
        JSONArray projects = new JSONArray();
        for (String path : preferences.getStringSet("project_directories_v1", new HashSet<>())) projects.put(path);
        return decode(new JSONObject().put("format", "pocket-terminal-workspace").put("version", 1)
            .put("accent", preferences.getString(StudioUi.ACCENT_KEY, "lime"))
            .put("focus", preferences.getBoolean("focus_mode", false)).put("font", font)
            .put("panes", preferences.getInt(StudioPanes.KEY, 0)).put("projects", projects)
            .put("snippets", snippets).put("hosts", new JSONArray(preferences.getString(HostStore.KEY, "[]")))
            .put("sessions", sessions).toString());
    }

    static WorkspaceState decode(String source) throws JSONException {
        if (source.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new JSONException("Workspace file is larger than 2 MiB.");
        int depth = 0;
        boolean string = false, escaped = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (string) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') string = false;
            } else if (c == '"') string = true;
            else if (c == '{' || c == '[') { if (++depth > 16) throw new JSONException("Workspace structure is too deeply nested."); }
            else if (c == '}' || c == ']') depth--;
        }
        JSONObject data = new JSONObject(source);
        if (!data.getString("format").equals("pocket-terminal-workspace") || integer(data, "version", 1, 1) != 1)
            throw new JSONException("Unsupported workspace format.");
        String accent = data.getString("accent");
        boolean known = false;
        for (String candidate : StudioUi.ACCENT_IDS) if (candidate.equals(accent)) known = true;
        if (!known) throw new JSONException("Unknown workspace accent.");
        if (!(data.get("focus") instanceof Boolean)) throw new JSONException("Invalid focus mode.");
        integer(data, "font", 4, 256); integer(data, "panes", 0, 2);
        JSONArray projects = data.getJSONArray("projects");
        if (projects.length() > 30) throw new JSONException("A workspace supports up to 30 projects.");
        for (int i = 0; i < projects.length(); i++) path(projects.getString(i));
        JSONArray snippets = data.getJSONArray("snippets");
        if (snippets.length() > SnippetStore.MAX_SNIPPETS) throw new JSONException("Too many snippets.");
        Set<String> ids = new HashSet<>();
        try {
            for (int i = 0; i < snippets.length(); i++) {
                JSONObject item = snippets.getJSONObject(i);
                CommandSnippet snippet = new CommandSnippet(item.getString("id"), item.getString("name"), item.getString("command"));
                if (!ids.add(snippet.id)) throw new JSONException("Duplicate snippet identifier.");
            }
            HostStore.decode(data.getJSONArray("hosts").toString());
        } catch (IllegalArgumentException e) { throw new JSONException(e.getMessage()); }
        JSONArray sessions = data.getJSONArray("sessions");
        if (sessions.length() > 8) throw new JSONException("A layout supports up to eight shells.");
        for (int i = 0; i < sessions.length(); i++) {
            JSONObject session = sessions.getJSONObject(i);
            path(session.getString("directory"));
            String name = session.getString("name");
            if (name.length() > 60 || !WorkspacePaths.safeLabel(name).equals(name)) throw new JSONException("Invalid shell name.");
        }
        // Only fields understood by this version survive validation.
        JSONObject result = new JSONObject();
        for (String key : new String[]{"format", "version", "accent", "focus", "font", "panes", "projects", "snippets", "hosts", "sessions"})
            result.put(key, data.get(key));
        return new WorkspaceState(result);
    }

    private static int integer(JSONObject data, String key, int min, int max) throws JSONException {
        Object value = data.get(key);
        if (!(value instanceof Integer) || (Integer) value < min || (Integer) value > max)
            throw new JSONException("Invalid " + key + ".");
        return (Integer) value;
    }
    private static void path(String value) throws JSONException {
        if (!value.startsWith("/") || value.length() > 4096 || !WorkspacePaths.safeLabel(value).equals(value))
            throw new JSONException("Workspace directories must be absolute filesystem paths without control characters.");
    }

    void apply(SharedPreferences preferences) throws JSONException {
        Set<String> projects = new HashSet<>();
        JSONArray array = json.getJSONArray("projects");
        for (int i = 0; i < array.length(); i++) projects.add(array.getString(i));
        if (!preferences.edit().putString(StudioUi.ACCENT_KEY, json.getString("accent"))
            .putBoolean("focus_mode", json.getBoolean("focus")).putInt(StudioPanes.KEY, json.getInt("panes"))
            .putStringSet("project_directories_v1", projects).putString(SnippetStore.KEY, json.getJSONArray("snippets").toString())
            .putString(HostStore.KEY, json.getJSONArray("hosts").toString())
            .putString(LAYOUT_KEY, json.getJSONArray("sessions").toString()).commit())
            throw new JSONException("Unable to save workspace settings.");
    }
}
