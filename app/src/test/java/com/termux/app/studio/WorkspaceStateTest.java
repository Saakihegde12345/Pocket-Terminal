/* SPDX-License-Identifier: GPL-3.0-only */
package com.termux.app.studio;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.Arrays;
import java.util.HashSet;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
public class WorkspaceStateTest {
    private SharedPreferences preferences;
    @Before public void setUp() {
        preferences = RuntimeEnvironment.getApplication().getSharedPreferences("workspace-test", Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
    }
    private WorkspaceState snapshot() throws Exception {
        return WorkspaceState.capture(preferences, 24, new JSONArray().put(
            new JSONObject().put("name", "Project").put("directory", "/data/data/com.termux/files/home/project")));
    }
    private void rejects(JSONObject json) throws Exception {
        try { WorkspaceState.decode(json.toString()); fail("Accepted invalid workspace"); }
        catch (JSONException expected) { }
    }
    @Test public void roundTripRetainsMetadataWithoutProcessExecution() throws Exception {
        preferences.edit().putString(StudioUi.ACCENT_KEY, "orchid").putBoolean("focus_mode", true)
            .putInt(StudioPanes.KEY, 2).putStringSet("project_directories_v1", new HashSet<>(Arrays.asList("/home/project with spaces"))).commit();
        new HostStore(preferences).save(new HostProfile("id", "Server", "::1", "dev", 2222));
        new SnippetStore(preferences).save(new CommandSnippet("mine", "Work", "printf '%s' '$literal'"));
        WorkspaceState state = WorkspaceState.decode(snapshot().json.toString());
        preferences.edit().clear().commit();
        state.apply(preferences);
        assertEquals("orchid", preferences.getString(StudioUi.ACCENT_KEY, ""));
        assertTrue(preferences.getBoolean("focus_mode", false));
        assertEquals(2, preferences.getInt(StudioPanes.KEY, 0));
        assertEquals("::1", new HostStore(preferences).load().get(0).host);
        assertEquals(4, new SnippetStore(preferences).load().size());
        assertEquals("Project", new JSONArray(preferences.getString(WorkspaceState.LAYOUT_KEY, "[]")).getJSONObject(0).getString("name"));
    }
    @Test public void unknownFieldsCannotWriteArbitraryPreferences() throws Exception {
        JSONObject json = snapshot().json.put("allow-external-apps", true).put("command", "rm -rf anything");
        WorkspaceState state = WorkspaceState.decode(json.toString());
        assertFalse(state.json.has("command"));
        state.apply(preferences);
        assertFalse(preferences.contains("allow-external-apps"));
        assertFalse(preferences.contains("command"));
    }
    @Test public void invalidScalarValuesAndVersionsAreRejected() throws Exception {
        rejects(snapshot().json.put("version", 2));
        rejects(snapshot().json.put("version", "1"));
        rejects(snapshot().json.put("panes", 3));
        rejects(snapshot().json.put("panes", 1.5));
        rejects(snapshot().json.put("accent", "unknown"));
        rejects(snapshot().json.put("focus", "false"));
        rejects(snapshot().json.put("font", 10000));
    }
    @Test public void malformedCommandsHostsAndPathsAreRejectedBeforeApplying() throws Exception {
        JSONObject json = snapshot().json;
        json.getJSONArray("snippets").getJSONObject(0).put("command", "pwd\nrm anything");
        rejects(json);
        rejects(snapshot().json.put("projects", new JSONArray().put("relative/path")));
        rejects(snapshot().json.put("projects", new JSONArray().put("/home/a\ncommand")));
        rejects(snapshot().json.put("hosts", new JSONArray().put(new JSONObject().put("id", "id")
            .put("name", "Name").put("host", "-oProxyCommand=bad").put("user", "dev").put("port", 22))));
        assertTrue(preferences.getAll().isEmpty());
    }
    @Test public void oversizedCollectionsAndDuplicateIdsAreRejected() throws Exception {
        JSONObject json = snapshot().json;
        JSONArray snippets = json.getJSONArray("snippets"); snippets.put(snippets.getJSONObject(0));
        rejects(json);
        JSONArray projects = new JSONArray();
        for (int i = 0; i < 31; i++) projects.put("/home/p" + i);
        rejects(snapshot().json.put("projects", projects));
        JSONArray sessions = new JSONArray();
        for (int i = 0; i < 9; i++) sessions.put(new JSONObject().put("name", "Shell").put("directory", "/home"));
        rejects(snapshot().json.put("sessions", sessions));
    }
    @Test public void emptyLibrariesStayEmptyAndUnrelatedPreferencesSurvive() throws Exception {
        JSONObject json = snapshot().json.put("snippets", new JSONArray()).put("hosts", new JSONArray());
        preferences.edit().putString("other-setting", "preserved").commit();
        WorkspaceState.decode(json.toString()).apply(preferences);
        assertTrue(new SnippetStore(preferences).load().isEmpty());
        assertTrue(new HostStore(preferences).load().isEmpty());
        assertEquals("preserved", preferences.getString("other-setting", ""));
    }
    @Test public void deeplyNestedAndOversizedDocumentsAreRejected() throws Exception {
        String nested = "{}";
        for (int i = 0; i < 20; i++) nested = "[" + nested + "]";
        try { WorkspaceState.decode(nested); fail(); } catch (JSONException expected) { }
        char[] huge = new char[WorkspaceState.MAX_BYTES + 1]; Arrays.fill(huge, ' ');
        try { WorkspaceState.decode(new String(huge)); fail(); } catch (JSONException expected) { }
    }
}
