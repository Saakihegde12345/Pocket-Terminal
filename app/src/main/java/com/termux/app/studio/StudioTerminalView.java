/* SPDX-License-Identifier: GPL-3.0-only */
package com.termux.app.studio;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import com.termux.app.TermuxActivity;
import com.termux.view.TerminalView;

/** Select input routing before touch, mouse reporting, accessibility focus or IME input. */
public final class StudioTerminalView extends TerminalView {
    public StudioTerminalView(Context context, AttributeSet attrs) { super(context, attrs); }
    private void activate() {
        Context context = getContext();
        while (!(context instanceof TermuxActivity) && context instanceof ContextWrapper) {
            Context base = ((ContextWrapper) context).getBaseContext();
            if (base == context) break;
            context = base;
        }
        if (context instanceof TermuxActivity) ((TermuxActivity) context).activateStudioTerminalView(this);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) { activate(); requestFocus(); }
        return super.onTouchEvent(event);
    }
    @Override protected void onFocusChanged(boolean focused, int direction, Rect previous) {
        if (focused) activate();
        super.onFocusChanged(focused, direction, previous);
    }
}
