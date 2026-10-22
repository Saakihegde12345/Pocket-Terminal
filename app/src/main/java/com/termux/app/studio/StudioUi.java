/* SPDX-License-Identifier: GPL-3.0-only */
package com.termux.app.studio;

import android.content.Context;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.LinearLayout;

/** Styling for app chrome only. Never changes a user's terminal color scheme. */
final class StudioUi {
    static final int BACKGROUND = Color.rgb(11, 14, 13);
    static final int SURFACE = Color.rgb(21, 27, 23);
    static final int TEXT = Color.rgb(235, 240, 237);
    static final int MUTED = Color.rgb(155, 170, 161);
    static final int ACCENT = Color.rgb(198, 248, 104);
    static final int BORDER = Color.rgb(43, 54, 46);
    static final int SELECTED = Color.rgb(36, 48, 25);
    static final String PREFERENCES = "terminal_studio_ui_v1";
    static final String ACCENT_KEY = "workspace_accent_v1";
    static final String[] ACCENT_IDS = {"lime", "glacier", "orchid", "amber"};
    static final int[] ACCENT_COLORS = {ACCENT, 0xff80dcff, 0xffd4b0ff, 0xffffce80};

    private StudioUi() {}

    static int accentIndex(Context context) {
        String saved = context.getSharedPreferences(PREFERENCES, 0).getString(ACCENT_KEY, ACCENT_IDS[0]);
        for (int i = 0; i < ACCENT_IDS.length; i++) if (ACCENT_IDS[i].equals(saved)) return i;
        return 0;
    }

    static int accent(Context context) { return ACCENT_COLORS[accentIndex(context)]; }

    static int selectedSurface(Context context) {
        int color = accent(context);
        return Color.rgb((Color.red(color) + 4 * Color.red(SURFACE)) / 5,
            (Color.green(color) + 4 * Color.green(SURFACE)) / 5,
            (Color.blue(color) + 4 * Color.blue(SURFACE)) / 5);
    }

    static LinearLayout column(Context context) {
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setBackgroundColor(SURFACE);
        int inset = dp(context, 16);
        body.setPadding(inset, dp(context, 8), inset, dp(context, 12));
        return body;
    }

    static EditText input(Context context, int hint) {
        EditText input = new EditText(context);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setTextSize(15);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setBackgroundTintList(ColorStateList.valueOf(accent(context)));
        input.setMinHeight(dp(context, 48));
        return input;
    }

    static void styleDialog(Context context, AlertDialog dialog) {
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.background_dark);
        for (int button : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
            TextView view = dialog.getButton(button);
            if (view != null) view.setTextColor(accent(context));
        }
    }

    /** Keep dialog actions reachable when an IME or a small window reduces the visible frame. */
    static void fitDialogContent(Context context, AlertDialog dialog, View content, int maximumDp, int overheadDp) {
        if (dialog.getWindow() == null) return;
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        View decor = dialog.getWindow().getDecorView();
        android.view.ViewTreeObserver.OnGlobalLayoutListener listener = () -> {
            Rect frame = new Rect(); decor.getWindowVisibleDisplayFrame(frame);
            int height = Math.max(dp(context, 48), Math.min(dp(context, maximumDp), frame.height() - dp(context, overheadDp)));
            android.view.ViewGroup.LayoutParams params = content.getLayoutParams();
            if (params != null && params.height != height) { params.height = height; content.setLayoutParams(params); }
        };
        decor.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        content.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View view) { }
            public void onViewDetachedFromWindow(View view) {
                if (decor.getViewTreeObserver().isAlive()) decor.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
                content.removeOnAttachStateChangeListener(this);
            }
        });
        listener.onGlobalLayout();
    }

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
        TextView button = text(context, label, 13, selected ? accent(context) : TEXT);
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
        button.setBackground(ripple(context, selected ? selectedSurface(context) : SURFACE, 11));
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
        return new RippleDrawable(ColorStateList.valueOf((accent(context) & 0x00ffffff) | 0x33000000), shape, null);
    }
}
