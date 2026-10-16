/* SPDX-License-Identifier: GPL-3.0-only */
package com.termux.app.studio;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

/** Styling for app chrome only. Never changes a user's terminal color scheme. */
final class StudioUi {
    static final int BACKGROUND = Color.rgb(11, 14, 13);
    static final int SURFACE = Color.rgb(21, 27, 23);
    static final int TEXT = Color.rgb(235, 240, 237);
    static final int MUTED = Color.rgb(155, 170, 161);
    static final int ACCENT = Color.rgb(198, 248, 104);
    static final int BORDER = Color.rgb(43, 54, 46);
    static final int SELECTED = Color.rgb(36, 48, 25);

    private StudioUi() {}

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context context, String text, int size, int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setFontFeatureSettings("kern");
        return view;
    }

    static TextView button(Context context, String label, String description, boolean selected) {
        TextView button = text(context, label, 13, selected ? ACCENT : TEXT);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setMinHeight(dp(context, 48));
        button.setMinWidth(dp(context, 48));
        button.setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6));
        button.setContentDescription(description);
        button.setFocusable(true);
        button.setClickable(true);
        button.setSelected(selected);
        button.setBackground(ripple(context, selected ? SELECTED : SURFACE, 11));
        button.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host,
                    android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.Button");
            }
        });
        return button;
    }

    static RippleDrawable ripple(Context context, int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(context, radius));
        return new RippleDrawable(ColorStateList.valueOf(0x33c6f868), shape, null);
    }
}
