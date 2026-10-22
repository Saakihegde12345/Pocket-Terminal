/* SPDX-License-Identifier: GPL-3.0-only */
package com.termux.app.studio;

import android.app.Application;
import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import com.termux.app.TermuxActivity;
import com.termux.shared.termux.terminal.TermuxTerminalViewClientBase;
import com.termux.shared.termux.terminal.io.TerminalExtraKeys;
import com.termux.view.TerminalView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
public class StudioInputRoutingTest {
    @Test public void touchingPaneSelectsItsActivityThroughThemeWrapper() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        StudioTerminalView first = new StudioTerminalView(activity, null);
        StudioTerminalView second = new StudioTerminalView(new ContextThemeWrapper(activity,
            android.R.style.Theme_Material_NoActionBar), null);
        first.setTerminalViewClient(new TermuxTerminalViewClientBase());
        second.setTerminalViewClient(new TermuxTerminalViewClientBase());
        activity.activateStudioTerminalView(first);
        MotionEvent event = MotionEvent.obtain(0, 1, MotionEvent.ACTION_DOWN, 2, 2, 0);
        try { second.onTouchEvent(event); } finally { event.recycle(); }
        assertSame(second, activity.getTerminalView());
    }

    private static final class RecordingView extends TerminalView {
        final StringBuilder input = new StringBuilder();
        RecordingView(Context context) { super(context, null); }
        @Override public void inputCodePoint(int source, int codePoint, boolean ctrl, boolean alt) { input.appendCodePoint(codePoint); }
    }
    private static final class Keys extends TerminalExtraKeys {
        Keys(TerminalView view) { super(view); }
        void press(String key) { onTerminalExtraKeyButtonClick(null, key, false, false, false, false); }
    }
    @Test public void sharedExtraKeysWriteOnlyToTheSelectedPane() {
        Context context = RuntimeEnvironment.getApplication();
        RecordingView first = new RecordingView(context), second = new RecordingView(context);
        Keys keys = new Keys(first);
        keys.press("a"); keys.setTerminalView(second); keys.press("b"); keys.setTerminalView(first); keys.press("c");
        assertEquals("ac", first.input.toString());
        assertEquals("b", second.input.toString());
    }
}
