/* SPDX-License-Identifier: GPL-3.0-only */
package com.termux.app.studio;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import com.termux.R;
import com.termux.app.TermuxActivity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Saved SSH/SFTP endpoints; authentication stays in OpenSSH and its known_hosts checks. */
final class StudioHosts {
    private final TermuxActivity activity;
    private final HostStore store;
    private AlertDialog dialog;
    private boolean closed;
    StudioHosts(TermuxActivity activity, SharedPreferences preferences) {
        this.activity = activity; store = new HostStore(preferences);
    }

    private void show(AlertDialog next) {
        if (closed || activity.isFinishing() || activity.isDestroyed()) return;
        if (dialog != null) dialog.dismiss();
        dialog = next; next.show(); StudioUi.styleDialog(activity, next);
    }

    void showHosts() {
        List<HostProfile> hosts = store.load();
        List<String> labels = new ArrayList<>();
        labels.add(activity.getString(R.string.studio_new_host));
        for (HostProfile host : hosts) labels.add(host.toString());
        show(new AlertDialog.Builder(activity).setTitle(R.string.studio_hosts)
            .setItems(labels.toArray(new String[0]), (d, which) -> {
                if (which == 0) edit(null); else actions(hosts.get(which - 1));
            }).setNegativeButton(android.R.string.cancel, null).create());
    }

    private void actions(HostProfile host) {
        show(new AlertDialog.Builder(activity).setTitle(host.name)
            .setItems(new String[]{"Connect with SSH", "Connect with SFTP", "Edit host", "Delete host"}, (d, which) -> {
                if (which <= 1) connect(host, which == 1);
                else if (which == 2) edit(host);
                else show(new AlertDialog.Builder(activity).setTitle("Delete host?")
                    .setMessage(host.toString()).setPositiveButton("Delete", (a, b) -> { store.remove(host.id); showHosts(); })
                    .setNegativeButton(android.R.string.cancel, null).create());
            }).setNegativeButton(android.R.string.cancel, null).create());
    }

    private void connect(HostProfile host, boolean sftp) {
        show(new AlertDialog.Builder(activity).setTitle(sftp ? "Connect with SFTP" : "Connect with SSH")
            .setMessage(host.toString() + "\n\nOpens a new terminal session. OpenSSH handles passwords, keys and host verification.")
            .setPositiveButton("Connect", (d, which) -> StudioCommands.start(activity, sftp ? "sftp" : "ssh",
                host.arguments(sftp), host.name)).setNegativeButton(android.R.string.cancel, null).create());
    }

    private EditText field(LinearLayout body, String label, String value) {
        body.addView(StudioUi.text(activity, label, 13, StudioUi.MUTED));
        EditText input = StudioUi.input(activity, R.string.studio_snippet_name);
        input.setHint(label); input.setText(value); body.addView(input); return input;
    }

    private void edit(HostProfile host) {
        LinearLayout body = StudioUi.column(activity);
        EditText name = field(body, "Name", host == null ? "" : host.name);
        EditText address = field(body, "Hostname or IP address", host == null ? "" : host.host);
        EditText user = field(body, "Username", host == null ? "" : host.user);
        EditText port = field(body, "Port", host == null ? "22" : Integer.toString(host.port));
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        ScrollView scroll = new ScrollView(activity); scroll.addView(body);
        AlertDialog next = new AlertDialog.Builder(activity).setTitle(host == null ? R.string.studio_new_host : R.string.studio_hosts)
            .setView(scroll).setPositiveButton(R.string.studio_save_snippet, null)
            .setNegativeButton(android.R.string.cancel, null).create();
        show(next);
        next.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                store.save(new HostProfile(host == null ? UUID.randomUUID().toString() : host.id,
                    name.getText().toString().trim(), address.getText().toString().trim(),
                    user.getText().toString().trim(), Integer.parseInt(port.getText().toString())));
                showHosts();
            } catch (IllegalArgumentException e) { activity.showToast(e.getMessage(), true); }
        });
    }

    void close() { closed = true; if (dialog != null) dialog.dismiss(); }
}
