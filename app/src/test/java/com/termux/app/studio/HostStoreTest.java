/* SPDX-License-Identifier: GPL-3.0-only */
package com.termux.app.studio;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
public class HostStoreTest {
    private SharedPreferences preferences;
    private HostStore store;
    @Before public void setUp() {
        preferences = RuntimeEnvironment.getApplication().getSharedPreferences("host-test", Context.MODE_PRIVATE);
        preferences.edit().clear().commit(); store = new HostStore(preferences);
    }
    @Test public void saveEditDeleteSurviveReload() {
        store.save(new HostProfile("id", "Server", "host", "user", 22));
        store.save(new HostProfile("id", "Edited", "::1", "dev", 2222));
        HostProfile restored = new HostStore(preferences).load().get(0);
        assertEquals("Edited", restored.name); assertEquals(2222, restored.port);
        assertEquals("dev@[::1]", restored.arguments(true)[3]);
        store.remove("id"); assertTrue(new HostStore(preferences).load().isEmpty());
    }
    @Test public void atCapacityEditsWorkButNewHostsAreRejected() {
        for (int i = 0; i < HostStore.LIMIT; i++) store.save(new HostProfile("id" + i, "Host", "host", "user", 22));
        store.save(new HostProfile("id0", "Edited", "other", "user", 22));
        assertEquals("other", store.load().get(0).host);
        try { store.save(new HostProfile("extra", "Host", "host", "user", 22)); fail(); }
        catch (IllegalArgumentException expected) { }
        assertEquals(50, store.load().size());
    }
    @Test public void malformedMetadataAndFractionalPortsAreRejected() throws Exception {
        preferences.edit().putString(HostStore.KEY, "not json").commit(); assertTrue(store.load().isEmpty());
        String item = "{\"id\":\"id\",\"name\":\"Name\",\"host\":\"host\",\"user\":\"dev\",\"port\":22}";
        try { HostStore.decode("[" + item + "," + item + "]"); fail(); } catch (JSONException expected) { }
        try { HostStore.decode("[" + item.replace(":22", ":22.5") + "]"); fail(); } catch (JSONException expected) { }
    }
}
