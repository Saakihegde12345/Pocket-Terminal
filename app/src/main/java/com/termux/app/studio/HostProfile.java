/* SPDX-License-Identifier: GPL-3.0-only */
package com.termux.app.studio;

/** Connection settings are arguments to OpenSSH, never shell command text. */
public final class HostProfile {
    public final String id, name, host, user;
    public final int port;

    public HostProfile(String id, String name, String host, String user, int port) {
        if (id == null || !id.matches("[A-Za-z0-9-]{1,80}")) throw new IllegalArgumentException("Invalid host identifier.");
        if (name == null || name.trim().isEmpty() || name.length() > 60 || !WorkspacePaths.safeLabel(name).equals(name))
            throw new IllegalArgumentException("Use a name of 1–60 characters without control characters.");
        if (host == null || host.length() > 253 || !host.matches("[A-Za-z0-9:][A-Za-z0-9.:%-]*")
            || host.endsWith("-") || host.contains(".."))
            throw new IllegalArgumentException("Enter a hostname, IPv4 or IPv6 address without spaces or brackets.");
        if (user == null || !user.matches("[A-Za-z0-9_][A-Za-z0-9_.-]{0,63}"))
            throw new IllegalArgumentException("Enter an SSH username using letters, numbers, dots, underscores or hyphens.");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Port must be between 1 and 65535.");
        this.id = id; this.name = name; this.host = host; this.user = user; this.port = port;
    }

    public String[] arguments(boolean sftp) {
        String address = host.contains(":") ? "[" + host + "]" : host;
        return new String[]{sftp ? "-P" : "-p", Integer.toString(port), "--", user + "@" + (sftp ? address : host)};
    }

    @Override public String toString() { return name + " · " + user + "@" + host + ":" + port; }
}
