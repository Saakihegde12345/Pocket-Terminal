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

final class HostStore {
    static final String KEY = "saved_hosts_v1";
    static final int LIMIT = 50;
    private final SharedPreferences preferences;
    HostStore(SharedPreferences preferences) { this.preferences = preferences; }

    static List<HostProfile> decode(String json) throws JSONException {
        JSONArray array = new JSONArray(json);
        if (array.length() > LIMIT) throw new JSONException("Too many saved hosts.");
        List<HostProfile> items = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            if (!(item.get("port") instanceof Integer)) throw new JSONException("Invalid host port.");
            HostProfile host = new HostProfile(item.getString("id"), item.getString("name"),
                item.getString("host"), item.getString("user"), item.getInt("port"));
            if (!ids.add(host.id)) throw new JSONException("Duplicate host identifier.");
            items.add(host);
        }
        return items;
    }

    List<HostProfile> load() {
        try { return decode(preferences.getString(KEY, "[]")); }
        catch (JSONException | IllegalArgumentException e) { return new ArrayList<>(); }
    }

    void save(HostProfile host) {
        List<HostProfile> items = load();
        int index = -1;
        for (int i = 0; i < items.size(); i++) if (items.get(i).id.equals(host.id)) index = i;
        if (index >= 0) items.set(index, host);
        else {
            if (items.size() >= LIMIT) throw new IllegalArgumentException("You can save up to 50 hosts.");
            items.add(host);
        }
        write(items);
    }

    void remove(String id) {
        List<HostProfile> items = load();
        for (int i = items.size() - 1; i >= 0; i--) if (items.get(i).id.equals(id)) items.remove(i);
        write(items);
    }

    private void write(List<HostProfile> items) {
        JSONArray array = new JSONArray();
        try {
            for (HostProfile item : items) array.put(new JSONObject().put("id", item.id).put("name", item.name)
                .put("host", item.host).put("user", item.user).put("port", item.port));
        } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
        preferences.edit().putString(KEY, array.toString()).apply();
    }
}
