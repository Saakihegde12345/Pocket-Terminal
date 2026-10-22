/* SPDX-License-Identifier: GPL-3.0-only */
package com.termux.app.studio;

public final class PackageEntry {
    public final String name, description;
    public PackageEntry(String name, String description) {
        if (name == null || name.length() > 160 || !name.matches("[a-z0-9][a-z0-9+.-]*"))
            throw new IllegalArgumentException("Invalid package name.");
        this.name = name; this.description = WorkspacePaths.safeLabel(description == null ? "" : description);
    }
    public static PackageEntry parse(String line) {
        int split = line.indexOf(" - ");
        if (split <= 0) throw new IllegalArgumentException("Invalid catalog entry.");
        return new PackageEntry(line.substring(0, split), line.substring(split + 3));
    }
    public static String searchExpression(String query) {
        if (query.length() > 80 || !WorkspacePaths.safeLabel(query).equals(query))
            throw new IllegalArgumentException("Use a package name up to 80 characters.");
        if (query.isEmpty()) return ".";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if ("\\.[]{}()*+?^$|".indexOf(c) >= 0) result.append('\\');
            result.append(c);
        }
        return result.toString();
    }
    @Override public String toString() { return name + "\n" + description; }
}
