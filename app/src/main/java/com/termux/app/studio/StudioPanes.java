/* SPDX-License-Identifier: GPL-3.0-only */
package com.termux.app.studio;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;
import java.util.ArrayList;
import java.util.List;

/** Two independent PTYs with one selected input target; resizing uses the existing TerminalView. */
final class StudioPanes {
    static final String KEY = "pane_layout_v1";
    private final TermuxActivity activity;
    private final SharedPreferences preferences;
    private final LinearLayout container, first, second;
    private final TextView firstLabel, secondLabel;
    private final TerminalView primary, secondary;
    private int layout;
    private int fontSize = -1;
    private int arrangedLayout = -1;
    private boolean arrangedSplit;
    private boolean restored;
    private boolean closed;

    StudioPanes(TermuxActivity activity, SharedPreferences preferences) {
        this.activity = activity; this.preferences = preferences;
        primary = activity.getTerminalView();
        secondary = new StudioTerminalView(activity, null);
        secondary.setTerminalViewClient(activity.getTermuxTerminalViewClient());
        secondary.setTextSize(activity.getPreferences().getFontSize());
        secondary.setTypeface(primary.getTypeface());
        secondary.setFocusableInTouchMode(true);
        activity.registerForContextMenu(secondary);
        container = activity.findViewById(R.id.studio_terminal_panes);
        container.removeView(primary);
        first = pane(primary); second = pane(secondary);
        firstLabel = (TextView) first.getChildAt(0); secondLabel = (TextView) second.getChildAt(0);
        container.addView(first); container.addView(second);
        layout = preferences.getInt(KEY, 0);
        if (layout < 0 || layout > 2) layout = 0;
        arrange();
    }

    private LinearLayout pane(TerminalView terminal) {
        LinearLayout pane = new LinearLayout(activity); pane.setOrientation(LinearLayout.VERTICAL);
        TextView label = StudioUi.button(activity, "", "Select terminal pane", false);
        label.setOnClickListener(v -> { activity.activateStudioTerminalView(terminal); terminal.requestFocus(); refresh(); });
        pane.addView(label, new LinearLayout.LayoutParams(-1, StudioUi.dp(activity, 48)));
        pane.addView(terminal, new LinearLayout.LayoutParams(-1, 0, 1));
        return pane;
    }

    TerminalView[] views() { return new TerminalView[]{primary, secondary}; }

    /** A live session must never be attached to two visible views with competing sizes. */
    void select(TerminalSession session) {
        if (layout == 0) return;
        if (secondary.getCurrentSession() == session) activity.activateStudioTerminalView(secondary);
        else if (primary.getCurrentSession() == session) activity.activateStudioTerminalView(primary);
    }

    void showOptions() {
        new AlertDialog.Builder(activity).setTitle(R.string.studio_panes)
            .setItems(new String[]{"Single pane", "Split top / bottom", "Split side by side", "Choose session for other pane"},
                (d, which) -> { if (which == 3) chooseSession(); else setLayout(which, true); })
            .setNegativeButton(android.R.string.cancel, null).show();
    }

    private TerminalSession otherSession() {
        TermuxService service = activity.getTermuxService();
        if (service == null) return null;
        String handle = preferences.getString("second_pane_session_v1", "");
        TerminalSession fallback = null;
        for (TermuxSession item : service.getTermuxSessions()) {
            TerminalSession session = item.getTerminalSession();
            if (session == null || session == primary.getCurrentSession()) continue;
            if (session.mHandle.equals(handle)) return session;
            if (session.isRunning()) fallback = session;
        }
        return fallback;
    }

    private void setLayout(int next, boolean createIfNeeded) {
        if (closed) return;
        TermuxService service = activity.getTermuxService();
        if (next != 0 && (service == null || service.wantsToStop() || activity.getCurrentSession() == null)) {
            activity.showToast(activity.getString(R.string.studio_wait_for_shell), true); return;
        }
        if (next != 0 && secondary.getCurrentSession() == null) {
            TerminalSession session = otherSession();
            if (session == null && createIfNeeded && service.getTermuxSessionsSize() < 8) {
                TermuxSession created = service.createTermuxSession(null, null, null,
                    activity.getCurrentSession().getCwd(), false, null);
                if (created != null) session = created.getTerminalSession();
            }
            if (session == null) { activity.showToast("Open another session before splitting the terminal.", true); return; }
            secondary.attachSession(session);
            preferences.edit().putString("second_pane_session_v1", session.mHandle).apply();
        }
        if (next == 0 && activity.getTerminalView() == secondary) {
            TerminalSession selected = secondary.getCurrentSession();
            activity.activateStudioTerminalView(primary);
            secondary.attachSession(null);
            primary.attachSession(selected);
        }
        if (next == 0) secondary.attachSession(null);
        layout = next;
        preferences.edit().putInt(KEY, next).apply();
        arrange(); refresh();
        activity.getTerminalView().requestFocus();
    }

    private void arrange() {
        boolean split = layout != 0 && secondary.getCurrentSession() != null;
        if (arrangedLayout == layout && arrangedSplit == split) return;
        arrangedLayout = layout; arrangedSplit = split;
        container.setOrientation(layout == 2 ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        first.setLayoutParams(new LinearLayout.LayoutParams(layout == 2 && split ? 0 : -1,
            layout == 2 && split ? -1 : 0, 1));
        second.setLayoutParams(new LinearLayout.LayoutParams(layout == 2 ? 0 : -1, layout == 2 ? -1 : 0, 1));
        second.setVisibility(split ? View.VISIBLE : View.GONE);
        firstLabel.setVisibility(split ? View.VISIBLE : View.GONE);
    }

    private void chooseSession() {
        TermuxService service = activity.getTermuxService();
        if (service == null) return;
        List<TerminalSession> sessions = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (TermuxSession item : service.getTermuxSessions()) {
            TerminalSession session = item.getTerminalSession();
            if (session == null || session == activity.getCurrentSession()) continue;
            sessions.add(session); labels.add(WorkspacePaths.safeLabel(session.mSessionName == null ? "Shell" : session.mSessionName));
        }
        if (sessions.isEmpty()) { activity.showToast("Open another session first.", true); return; }
        new AlertDialog.Builder(activity).setTitle("Other pane session").setItems(labels.toArray(new String[0]), (d, which) -> {
            TerminalView other = activity.getTerminalView() == primary ? secondary : primary;
            other.attachSession(sessions.get(which));
            preferences.edit().putString("second_pane_session_v1", secondary.getCurrentSession().mHandle).apply();
            if (layout == 0) setLayout(1, false); else refresh();
        }).setNegativeButton(android.R.string.cancel, null).show();
    }

    void refresh() {
        if (closed) return;
        TermuxService service = activity.getTermuxService();
        if (!restored && service != null && primary.getCurrentSession() != null) {
            restored = true;
            if (layout != 0) {
                TerminalSession left = service.getTerminalSessionForHandle(preferences.getString("first_pane_session_v1", ""));
                TerminalSession right = service.getTerminalSessionForHandle(preferences.getString("second_pane_session_v1", ""));
                boolean rightActive = preferences.getBoolean("second_pane_active_v1", false);
                if (left != null && right != null && left != right) {
                    primary.attachSession(null); secondary.attachSession(null);
                    primary.attachSession(left); secondary.attachSession(right);
                    activity.activateStudioTerminalView(rightActive ? secondary : primary);
                }
            }
        }
        if (layout != 0 && service != null && primary.getCurrentSession() != null) {
            TerminalSession session = secondary.getCurrentSession();
            if (session == null || service.getIndexOfSession(session) < 0 || session == primary.getCurrentSession()) {
                TerminalSession replacement = otherSession();
                if (replacement != null) secondary.attachSession(replacement);
                else if (session != null) {
                    activity.activateStudioTerminalView(primary);
                    setLayout(0, false); return;
                }
            }
        }
        if (fontSize != activity.getPreferences().getFontSize()) {
            fontSize = activity.getPreferences().getFontSize();
            primary.setTextSize(fontSize); secondary.setTextSize(fontSize);
        }
        firstLabel.setText(label(primary, 1)); secondLabel.setText(label(secondary, 2));
        firstLabel.setContentDescription(label(primary, 1) + ". Select first terminal pane.");
        secondLabel.setContentDescription(label(secondary, 2) + ". Select second terminal pane.");
        firstLabel.setSelected(activity.getTerminalView() == primary);
        secondLabel.setSelected(activity.getTerminalView() == secondary);
        firstLabel.setTextColor(activity.getTerminalView() == primary ? StudioUi.accent(activity) : StudioUi.MUTED);
        secondLabel.setTextColor(activity.getTerminalView() == secondary ? StudioUi.accent(activity) : StudioUi.MUTED);
        arrange();
        remember();
    }

    void remember() {
        if (!restored || closed || layout == 0 || primary.getCurrentSession() == null || secondary.getCurrentSession() == null) return;
        String left = primary.getCurrentSession().mHandle, right = secondary.getCurrentSession().mHandle;
        boolean active = activity.getTerminalView() == secondary;
        if (!left.equals(preferences.getString("first_pane_session_v1", ""))
            || !right.equals(preferences.getString("second_pane_session_v1", ""))
            || active != preferences.getBoolean("second_pane_active_v1", false))
            preferences.edit().putString("first_pane_session_v1", left).putString("second_pane_session_v1", right)
                .putBoolean("second_pane_active_v1", active).apply();
    }

    private String label(TerminalView view, int number) {
        TerminalSession session = view.getCurrentSession();
        return number + (activity.getTerminalView() == view ? " · Active · " : " · ")
            + WorkspacePaths.safeLabel(session == null || session.mSessionName == null ? "Shell" : session.mSessionName);
    }
    void close() { closed = true; secondary.setTerminalCursorBlinkerState(false, true); }
}
