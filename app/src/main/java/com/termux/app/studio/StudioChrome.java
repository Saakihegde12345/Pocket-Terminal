/* SPDX-License-Identifier: GPL-3.0-only */
package com.termux.app.studio;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.shared.termux.TermuxConstants;
import com.termux.terminal.TerminalSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Native workspace chrome around the unmodified terminal view, IME and PTY pipeline. */
public final class StudioChrome {
    private final TermuxActivity activity;
    private final SharedPreferences preferences;
    private final StudioProjectPicker projects;
    private final LinearLayout root;
    private final LinearLayout header;
    private final TextView directory;
    private final TextView subtitle;
    private final LinearLayout tabs;
    private final HorizontalScrollView tabScroll;
    private final LinearLayout navigation;
    private boolean focusMode;
    private boolean compact;
    private boolean disposed;
    private boolean refreshQueued;
    private String sessionFingerprint = "";
    private AlertDialog palette;

    private final Runnable refreshTask = this::runRefresh;

    private void runRefresh() {
        refreshQueued = false;
        if (!disposed && !activity.isFinishing() && !activity.isDestroyed()) refreshNow();
    }

    private final View.OnLayoutChangeListener sizeListener = this::onLayoutChange;

    private void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
        boolean next = compact ? b - t < dp(430) : b - t < dp(390);
        if (compact != next) {
            compact = next;
            applyFocusMode();
        }
    }

    public StudioChrome(TermuxActivity activity) {
        this.activity = activity;
        preferences = activity.getSharedPreferences("terminal_studio_ui_v1", 0);
        projects = new StudioProjectPicker(activity, preferences);
        focusMode = preferences.getBoolean("focus_mode", false);
        root = activity.findViewById(R.id.activity_termux_root_view);

        header = row();
        header.setPadding(dp(16), dp(8), dp(12), dp(8));
        TextView mark = StudioUi.button(activity, "❯_", activity.getString(R.string.studio_toggle_focus), false);
        mark.setTextSize(19);
        mark.setTypeface(Typeface.MONOSPACE);
        mark.setTextColor(StudioUi.BACKGROUND);
        mark.setBackground(StudioUi.ripple(activity, StudioUi.ACCENT, 12));
        mark.setPadding(0, 0, 0, 0);
        mark.setOnClickListener(v -> toggleFocusMode());
        header.addView(mark, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout brand = new LinearLayout(activity);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setPadding(dp(12), 0, dp(8), 0);
        TextView title = StudioUi.text(activity, activity.getString(R.string.studio_brand), 16, StudioUi.TEXT);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setLetterSpacing(0.13f);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        subtitle = StudioUi.text(activity, activity.getString(R.string.studio_preparing), 11, StudioUi.MUTED);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        brand.addView(title);
        brand.addView(subtitle);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView actions = StudioUi.button(activity, "⌘", activity.getString(R.string.studio_actions), false);
        actions.setTextSize(23);
        actions.setOnClickListener(v -> showPalette());
        header.addView(actions, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header, 0, new LinearLayout.LayoutParams(-1, -2));

        directory = StudioUi.button(activity, "~", activity.getString(R.string.studio_projects), false);
        directory.setGravity(Gravity.CENTER_VERTICAL);
        directory.setTextSize(12);
        directory.setTypeface(Typeface.MONOSPACE);
        directory.setEllipsize(TextUtils.TruncateAt.START);
        directory.setOnClickListener(v -> projects.showProjects());
        LinearLayout.LayoutParams directoryParams = new LinearLayout.LayoutParams(-1, dp(48));
        directoryParams.setMargins(dp(16), 0, dp(16), dp(8));
        root.addView(directory, 1, directoryParams);

        tabScroll = new HorizontalScrollView(activity);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setFillViewport(true);
        tabScroll.setBackgroundColor(StudioUi.BACKGROUND);
        tabs = row();
        tabs.setPadding(dp(12), 0, dp(12), dp(8));
        tabScroll.addView(tabs, new HorizontalScrollView.LayoutParams(-2, -1));
        root.addView(tabScroll, 2, new LinearLayout.LayoutParams(-1, dp(56)));

        navigation = row();
        navigation.setPadding(dp(12), dp(5), dp(12), dp(5));
        addNavigation(R.string.studio_sessions, () -> activity.getDrawer().openDrawer(Gravity.LEFT), false);
        addNavigation(R.string.studio_projects, projects::showProjects, false);
        addNavigation(R.string.studio_keyboard,
            () -> activity.getTermuxTerminalViewClient().onToggleSoftKeyboardRequest(), false);
        // Keep the upstream bottom-space sentinel as the last child for IME overlap handling.
        root.addView(navigation, root.getChildCount() - 1, new LinearLayout.LayoutParams(-1, -2));
        root.addOnLayoutChangeListener(sizeListener);
        styleDrawer();
        applyFocusMode();
        refresh();
    }

    private int dp(int value) { return StudioUi.dp(activity, value); }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(StudioUi.BACKGROUND);
        return row;
    }

    private void addNavigation(int label, Runnable action, boolean selected) {
        String text = activity.getString(label);
        TextView button = StudioUi.button(activity, text, text, selected);
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(dp(3), 0, dp(3), 0);
        navigation.addView(button, params);
    }

    private void styleDrawer() {
        activity.findViewById(R.id.left_drawer).setBackgroundColor(StudioUi.SURFACE);
        android.widget.ImageButton settings = activity.findViewById(R.id.settings_button);
        settings.setColorFilter(StudioUi.ACCENT);
        settings.setMinimumHeight(dp(48));
        settings.setMinimumWidth(dp(48));
        ViewGroup.LayoutParams settingsSize = settings.getLayoutParams();
        settingsSize.width = dp(48);
        settingsSize.height = dp(48);
        settings.setLayoutParams(settingsSize);
        for (int id : new int[]{R.id.new_session_button, R.id.toggle_keyboard_button}) {
            TextView button = activity.findViewById(id);
            button.setTextColor(StudioUi.ACCENT);
        }
    }

    /** Event-driven and frame-coalesced. Terminal byte output does not rebuild app chrome. */
    public void refresh() {
        if (disposed || refreshQueued) return;
        refreshQueued = true;
        root.postOnAnimation(refreshTask);
    }

    private void refreshNow() {
        TermuxService service = activity.getTermuxService();
        TerminalSession current = activity.getCurrentSession();
        subtitle.setText(service == null || current == null ? activity.getString(R.string.studio_preparing)
            : activity.getResources().getQuantityString(R.plurals.studio_session_count,
                service.getTermuxSessionsSize(), service.getTermuxSessionsSize()));
        String cwd = current == null ? null : current.getCwd();
        String path = cwd == null ? activity.getString(R.string.studio_wait_for_shell)
            : WorkspacePaths.safeLabel(WorkspacePaths.displayPath(cwd, TermuxConstants.TERMUX_HOME_DIR_PATH));
        directory.setText("▸  " + path);
        directory.setContentDescription(activity.getString(R.string.studio_open_projects_at, path));

        List<TerminalSession> sessions = new ArrayList<>();
        if (service != null) for (TermuxSession entry : service.getTermuxSessions()) {
            TerminalSession session = entry.getTerminalSession();
            if (session != null) sessions.add(session);
        }
        StringBuilder fingerprint = new StringBuilder();
        for (TerminalSession session : sessions) fingerprint.append(session.mHandle).append('\0')
            .append(session.mSessionName).append('\0').append(session.getTitle()).append('\0')
            .append(session.isRunning()).append(session == current).append('\n');
        if (sessionFingerprint.equals(fingerprint.toString())) return;
        sessionFingerprint = fingerprint.toString();
        tabs.removeAllViews();
        TextView selected = null;
        int number = 0;
        for (TerminalSession session : sessions) {
            number++;
            String name = session.mSessionName;
            if (TextUtils.isEmpty(name)) name = session.getTitle();
            if (TextUtils.isEmpty(name)) name = activity.getString(R.string.studio_shell_number, number);
            name = WorkspacePaths.safeLabel(name);
            String fullLabel = name + (session.isRunning() ? "" : " · " + activity.getString(R.string.studio_exited));
            if (name.codePointCount(0, name.length()) > 22)
                name = name.substring(0, name.offsetByCodePoints(0, 22)) + "…";
            TextView tab = StudioUi.button(activity, (session.isRunning() ? "● " : "○ ") + name,
                fullLabel, session == current);
            tab.setOnClickListener(v -> {
                activity.getTermuxTerminalSessionClient().setCurrentSession(session);
                activity.getTerminalView().requestFocus();
            });
            tab.setOnLongClickListener(v -> {
                activity.getTermuxTerminalSessionClient().renameSession(session);
                return true;
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -1);
            params.setMargins(dp(4), 0, dp(4), 0);
            tabs.addView(tab, params);
            if (session == current) selected = tab;
        }
        TextView plus = StudioUi.button(activity, "+", activity.getString(R.string.studio_new_shell), false);
        plus.setTextSize(22);
        plus.setEnabled(current != null);
        plus.setOnClickListener(v -> activity.getTermuxTerminalSessionClient().addNewSession(false, null));
        tabs.addView(plus, new LinearLayout.LayoutParams(dp(48), -1));
        if (selected != null) {
            final TextView scrollTarget = selected;
            tabScroll.post(() -> {
                if (!disposed) tabScroll.smoothScrollTo(Math.max(0, scrollTarget.getLeft() - dp(12)), 0);
            });
        }
    }

    private void toggleFocusMode() {
        focusMode = !focusMode;
        preferences.edit().putBoolean("focus_mode", focusMode).apply();
        applyFocusMode();
    }

    private void applyFocusMode() {
        directory.setVisibility(focusMode || compact ? View.GONE : View.VISIBLE);
        tabScroll.setVisibility(focusMode ? View.GONE : View.VISIBLE);
        navigation.setVisibility(focusMode || compact ? View.GONE : View.VISIBLE);
    }

    private static final class Action {
        final String label;
        final Runnable run;
        Action(String label, Runnable run) { this.label = label; this.run = run; }
        @Override public String toString() { return label; }
    }

    public void showPalette() {
        if (disposed) return;
        if (palette != null) palette.dismiss();
        List<Action> all = new ArrayList<>();
        boolean ready = activity.getCurrentSession() != null;
        if (ready) {
            all.add(action(R.string.studio_new_shell, () -> activity.getTermuxTerminalSessionClient().addNewSession(false, null)));
            all.add(action(R.string.studio_named_shell, () -> activity.findViewById(R.id.new_session_button).performLongClick()));
            all.add(action(R.string.studio_rename_shell, () -> activity.getTermuxTerminalSessionClient().renameSession(activity.getCurrentSession())));
        }
        all.add(action(R.string.studio_projects, projects::showProjects));
        all.add(action(R.string.studio_keyboard, () -> activity.getTermuxTerminalViewClient().onToggleSoftKeyboardRequest()));
        all.add(action(focusMode ? R.string.studio_leave_focus : R.string.studio_enter_focus, this::toggleFocusMode));
        all.add(action(R.string.studio_larger_text, () -> activity.getTermuxTerminalViewClient().changeFontSize(true)));
        all.add(action(R.string.studio_smaller_text, () -> activity.getTermuxTerminalViewClient().changeFontSize(false)));
        all.add(action(R.string.studio_extra_keys, activity::toggleTerminalToolbar));
        all.add(action(R.string.studio_settings, () -> activity.findViewById(R.id.settings_button).performClick()));

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(8), dp(12), dp(4));
        EditText search = new EditText(activity);
        search.setSingleLine(true);
        search.setTextColor(StudioUi.TEXT);
        search.setHintTextColor(StudioUi.MUTED);
        search.setHint(R.string.studio_find_action);
        body.addView(search, new LinearLayout.LayoutParams(-1, dp(56)));
        ArrayAdapter<Action> adapter = new ArrayAdapter<Action>(activity, android.R.layout.simple_list_item_1, new ArrayList<>(all)) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView row = (TextView) super.getView(position, convertView, parent);
                row.setTextColor(StudioUi.TEXT);
                row.setMinHeight(dp(48));
                return row;
            }
        };
        ListView list = new ListView(activity);
        list.setAdapter(adapter);
        int listHeight = Math.max(96, Math.min(280, activity.getResources().getConfiguration().screenHeightDp / 3));
        body.addView(list, new LinearLayout.LayoutParams(-1, dp(listHeight)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().toLowerCase(Locale.ROOT);
                adapter.setNotifyOnChange(false);
                adapter.clear();
                for (Action item : all) if (item.label.toLowerCase(Locale.ROOT).contains(query)) adapter.add(item);
                adapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        palette = new AlertDialog.Builder(new ContextThemeWrapper(activity, android.R.style.Theme_Material_Dialog_Alert))
            .setTitle(R.string.studio_actions).setView(body).setNegativeButton(android.R.string.cancel, null).create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            Action item = adapter.getItem(position);
            palette.dismiss();
            if (item != null && !disposed) item.run.run();
        });
        palette.show();
    }

    private Action action(int label, Runnable action) { return new Action(activity.getString(label), action); }

    public void close() {
        disposed = true;
        root.removeCallbacks(refreshTask);
        root.removeOnLayoutChangeListener(sizeListener);
        if (palette != null) palette.dismiss();
        palette = null;
        projects.close();
    }
}
