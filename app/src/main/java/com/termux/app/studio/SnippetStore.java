/* SPDX-License-Identifier: GPL-3.0-only */
package com.termux.app.studio;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Small local collection; no command history, clipboard monitoring or network access. */
final class SnippetStore {
    static final String KEY = "command_snippets_v1";
    static final int MAX_SNIPPETS = 50;
    private final SharedPreferences preferences;

    SnippetStore(SharedPreferences preferences) { this.preferences = preferences; }

    List<CommandSnippet> load() {
        List<CommandSnippet> result = new ArrayList<>();
        if (!preferences.contains(KEY)) {
            result.add(new CommandSnippet("starter-git", "Git status", "git status --short --branch"));
            result.add(new CommandSnippet("starter-files", "List files", "ls -lah"));
            result.add(new CommandSnippet("starter-space", "Directory sizes", "du -h --max-depth=1 ."));
            return result;
        }
        Set<String> ids = new HashSet<>();
        try {
            JSONArray items = new JSONArray(preferences.getString(KEY, "[]"));
            for (int i = 0; i < items.length() && result.size() < MAX_SNIPPETS; i++) {
                try {
                    JSONObject item = items.getJSONObject(i);
                    CommandSnippet snippet = new CommandSnippet(item.getString("id"), item.getString("name"), item.getString("command"));
                    if (ids.add(snippet.id)) result.add(snippet);
                } catch (JSONException | IllegalArgumentException ignored) { /* Skip invalid entries. */ }
            }
        } catch (JSONException ignored) { /* Malformed metadata cannot become terminal input. */ }
        return result;
    }

    boolean save(CommandSnippet snippet) {
        List<CommandSnippet> items = load();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id.equals(snippet.id)) {
                items.set(i, snippet);
                write(items);
                return true;
            }
        }
        if (items.size() >= MAX_SNIPPETS) return false;
        items.add(snippet);
        write(items);
        return true;
    }

    void remove(String id) {
        List<CommandSnippet> items = load();
        for (int i = items.size() - 1; i >= 0; i--) if (items.get(i).id.equals(id)) items.remove(i);
        write(items);
    }

    private void write(List<CommandSnippet> items) {
        JSONArray array = new JSONArray();
        try {
            for (CommandSnippet item : items) {
                JSONObject object = new JSONObject();
                object.put("id", item.id);
                object.put("name", item.name);
                object.put("command", item.command);
                array.put(object);
            }
        } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
        preferences.edit().putString(KEY, array.toString()).apply();
    }
}
