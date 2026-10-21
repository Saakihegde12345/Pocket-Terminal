/* SPDX-License-Identifier: GPL-3.0-only */
package com.termux.app.studio;

import android.app.Dialog;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.terminal.TerminalSession;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Read-only, bounded output inspector. Searching never reads a live buffer from a worker. */
final class StudioOutputSearch {
    private final TermuxActivity activity;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Dialog dialog;
    private boolean closed;

    StudioOutputSearch(TermuxActivity activity) { this.activity = activity; }

    void show() {
        if (closed || activity.isFinishing() || activity.isDestroyed()) return;
        TerminalSession session = activity.getCurrentSession();
        if (session == null || session.getEmulator() == null) {
            activity.showToast(activity.getString(R.string.studio_wait_for_shell), true);
            return;
        }
        if (dialog != null) dialog.dismiss();
        Panel panel = new Panel(session);
        dialog = panel.window;
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(-1, -1);
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
        panel.capture();
    }

    private final class Panel {
        final TerminalSession session;
        final Dialog window;
        final EditText query;
        final TextView output;
        final TextView status;
        final TextView source;
        final TextView previous;
        final TextView next;
        final TextView caseButton;
        final ScrollView scroll;
        final BackgroundColorSpan background = new BackgroundColorSpan(StudioUi.accent(activity));
        final ForegroundColorSpan foreground = new ForegroundColorSpan(StudioUi.BACKGROUND);
        final Runnable searchTask = this::search;
        OutputSnapshot snapshot;
        OutputSearch.Result result;
        Future<?> job;
        int generation;
        int selected = -1;
        boolean matchCase;
        boolean active = true;

        Panel(TerminalSession session) {
            this.session = session;
            window = new Dialog(new ContextThemeWrapper(activity, android.R.style.Theme_Material_NoActionBar));
            window.requestWindowFeature(Window.FEATURE_NO_TITLE);
            LinearLayout body = StudioUi.column(activity);
            LinearLayout heading = row();
            TextView title = StudioUi.text(activity, activity.getString(R.string.studio_search_output), 21, StudioUi.TEXT);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            TextView close = button("×", R.string.studio_close_search);
            close.setTextSize(24);
            close.setOnClickListener(v -> window.dismiss());
            heading.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
            body.addView(heading);
            source = StudioUi.text(activity, "", 11, StudioUi.MUTED);
            source.setMaxLines(2);
            source.setPadding(0, dp(6), 0, dp(8));
            body.addView(source);
            query = StudioUi.input(activity, R.string.studio_search_hint);
            query.setFilters(new InputFilter[]{new InputFilter.LengthFilter(OutputSearch.MAX_QUERY_LENGTH)});
            query.setContentDescription(activity.getString(R.string.studio_search_hint));
            query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
            body.addView(query, new LinearLayout.LayoutParams(-1, dp(56)));
            LinearLayout controls = row();
            previous = button("↑", R.string.studio_previous_match);
            next = button("↓", R.string.studio_next_match);
            previous.setTextSize(22);
            next.setTextSize(22);
            previous.setOnClickListener(v -> move(-1));
            next.setOnClickListener(v -> move(1));
            controls.addView(previous, new LinearLayout.LayoutParams(dp(48), dp(48)));
            controls.addView(next, new LinearLayout.LayoutParams(dp(48), dp(48)));
            caseButton = button("Aa", R.string.studio_match_case);
            caseButton.setContentDescription(activity.getString(R.string.studio_match_case_off));
            caseButton.setOnClickListener(v -> {
                matchCase = !matchCase;
                caseButton.setSelected(matchCase);
                caseButton.setTextColor(matchCase ? StudioUi.accent(activity) : StudioUi.TEXT);
                caseButton.setContentDescription(activity.getString(matchCase
                    ? R.string.studio_match_case_on : R.string.studio_match_case_off));
                requestSearch();
            });
            controls.addView(caseButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
            TextView refresh = button(activity.getString(R.string.studio_refresh), R.string.studio_refresh_output);
            refresh.setOnClickListener(v -> capture());
            LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(0, dp(48), 1);
            refreshParams.setMargins(dp(8), 0, 0, 0);
            controls.addView(refresh, refreshParams);
            body.addView(controls);
            status = StudioUi.text(activity, "", 12, StudioUi.MUTED);
            status.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
            status.setPadding(0, dp(8), 0, dp(8));
            body.addView(status);
            output = StudioUi.text(activity, "", 13, StudioUi.TEXT);
            output.setTypeface(Typeface.MONOSPACE);
            output.setTextIsSelectable(true);
            output.setGravity(Gravity.TOP | Gravity.START);
            output.setPadding(dp(12), dp(12), dp(12), dp(12));
            output.setBackgroundColor(StudioUi.BACKGROUND);
            output.setHorizontallyScrolling(false);
            scroll = new ScrollView(activity);
            scroll.setFillViewport(true);
            scroll.addView(output, new ScrollView.LayoutParams(-1, -2));
            body.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            TextView note = StudioUi.text(activity, activity.getString(R.string.studio_snapshot_note), 11, StudioUi.MUTED);
            note.setPadding(0, dp(8), 0, 0);
            body.addView(note);
            window.setContentView(body, new LinearLayout.LayoutParams(-1, -1));
            window.setOnDismissListener(d -> {
                active = false;
                generation++;
                query.removeCallbacks(searchTask);
                if (job != null) job.cancel(true);
                if (dialog == window) dialog = null;
            });
            query.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { requestSearch(); }
                @Override public void afterTextChanged(Editable s) {}
            });
            query.setOnEditorActionListener((v, action, event) -> {
                if (event == null || event.getAction() == KeyEvent.ACTION_DOWN) move(1);
                return true;
            });
        }

        int dp(int value) { return StudioUi.dp(activity, value); }
        LinearLayout row() {
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            return row;
        }
        TextView button(String label, int description) {
            return StudioUi.button(activity, label, activity.getString(description), false);
        }

        void capture() {
            if (!active || closed || session.getEmulator() == null) return;
            snapshot = OutputSnapshot.capture(session.getEmulator());
            String label = session.mSessionName;
            if (label == null || label.isEmpty()) label = session.getTitle();
            if (label == null || label.isEmpty()) label = activity.getString(R.string.studio_shell_number, 1);
            source.setText(activity.getString(snapshot.limited ? R.string.studio_snapshot_limited : R.string.studio_snapshot_source,
                WorkspacePaths.safeLabel(label)));
            output.setText(snapshot.text, TextView.BufferType.SPANNABLE);
            scroll.scrollTo(0, 0);
            requestSearch();
        }

        void requestSearch() {
            generation++;
            query.removeCallbacks(searchTask);
            if (job != null) job.cancel(true);
            result = null;
            selected = -1;
            clearHighlight();
            previous.setEnabled(false);
            next.setEnabled(false);
            status.setText(query.length() == 0 ? R.string.studio_search_prompt : R.string.studio_searching);
            if (active && !closed && snapshot != null) query.postDelayed(searchTask, 180);
        }

        void search() {
            if (!active || closed || snapshot == null) return;
            final int request = generation;
            final String text = snapshot.text;
            final String term = query.getText().toString();
            final boolean exact = matchCase;
            job = worker.submit(() -> {
                OutputSearch.Result found = OutputSearch.find(text, term, exact);
                query.post(() -> {
                    if (!active || closed || generation != request) return;
                    result = found;
                    previous.setEnabled(!found.matches.isEmpty());
                    next.setEnabled(!found.matches.isEmpty());
                    if (term.isEmpty()) status.setText(R.string.studio_search_prompt);
                    else if (found.matches.isEmpty()) status.setText(R.string.studio_no_matches);
                    else move(1);
                });
            });
        }

        void clearHighlight() {
            if (output.getText() instanceof Spannable) {
                Spannable text = (Spannable) output.getText();
                text.removeSpan(background);
                text.removeSpan(foreground);
            }
        }

        void move(int direction) {
            if (!active || result == null || result.matches.isEmpty()) return;
            selected = OutputSearch.nextIndex(selected, direction, result.matches.size());
            OutputSearch.Match match = result.matches.get(selected);
            clearHighlight();
            Spannable text = (Spannable) output.getText();
            text.setSpan(background, match.start, match.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(foreground, match.start, match.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            status.setText(activity.getString(R.string.studio_match_count, selected + 1,
                result.matches.size(), result.limited ? "+" : ""));
            final int request = generation;
            final int index = selected;
            output.post(() -> {
                if (active && request == generation && index == selected && output.getLayout() != null) {
                    int line = output.getLayout().getLineForOffset(match.start);
                    scroll.smoothScrollTo(0, Math.max(0, output.getLayout().getLineTop(line) - scroll.getHeight() / 3));
                }
            });
        }
    }

    void close() {
        closed = true;
        if (dialog != null) dialog.dismiss();
        dialog = null;
        worker.shutdownNow();
    }
}
