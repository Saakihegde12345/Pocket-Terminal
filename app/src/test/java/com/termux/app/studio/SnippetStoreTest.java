/* SPDX-License-Identifier: GPL-3.0-only */
package com.termux.app.studio;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
public class SnippetStoreTest {
    private SharedPreferences preferences;
    private SnippetStore store;

    @Before public void setUp() {
        preferences = RuntimeEnvironment.getApplication().getSharedPreferences("snippets-test", Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
        store = new SnippetStore(preferences);
    }

    @Test public void editsSurviveNewStoreAndPreserveCommand() {
        String command = "printf '%s' '日本語 with spaces'; ls";
        assertTrue(store.save(new CommandSnippet("my-id", "My command", command)));
        assertTrue(store.save(new CommandSnippet("my-id", "Renamed", command + " -a")));
        SnippetStore restored = new SnippetStore(preferences);
        assertEquals(4, restored.load().size());
        assertEquals("Renamed", restored.load().get(3).name);
        assertEquals(command + " -a", restored.load().get(3).command);
    }

    @Test public void deletingAllDoesNotRecreateStarterCommands() {
        for (CommandSnippet item : store.load()) store.remove(item.id);
        assertTrue(new SnippetStore(preferences).load().isEmpty());
    }

    @Test public void fullLibraryStillAllowsEdits() {
        preferences.edit().putString(SnippetStore.KEY, "[]").commit();
        for (int i = 0; i < SnippetStore.MAX_SNIPPETS; i++)
            assertTrue(store.save(new CommandSnippet("id" + i, "Command " + i, "pwd")));
        assertFalse(store.save(new CommandSnippet("extra", "One too many", "pwd")));
        assertTrue(store.save(new CommandSnippet("id0", "Edited", "ls")));
        assertEquals(SnippetStore.MAX_SNIPPETS, store.load().size());
        assertEquals("ls", store.load().get(0).command);
    }

    @Test public void invalidAndDuplicateMetadataCannotBecomeInput() {
        preferences.edit().putString(SnippetStore.KEY, "["
            + "{\"id\":\"ok\",\"name\":\"Valid\",\"command\":\"pwd\"},"
            + "{\"id\":\"ok\",\"name\":\"Duplicate\",\"command\":\"ls\"},"
            + "{\"id\":\"bad\",\"name\":\"Unsafe\",\"command\":\"pwd\\n\"},"
            + "{\"name\":\"Missing fields\"},null]").commit();
        assertEquals(1, store.load().size());
        assertEquals("pwd", store.load().get(0).command);
        preferences.edit().putString(SnippetStore.KEY, "not json").commit();
        assertTrue(store.load().isEmpty());
    }
}
