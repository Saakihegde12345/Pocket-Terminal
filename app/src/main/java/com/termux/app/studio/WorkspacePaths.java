/* SPDX-License-Identifier: GPL-3.0-only */
package com.termux.app.studio;

import java.io.File;
import java.io.IOException;

/** Filesystem paths stay data; they are never interpolated into shell commands. */
public final class WorkspacePaths {
    private WorkspacePaths() {}

    public static File resolveDirectory(String input, File home) throws IOException {
        if (input == null || input.isEmpty() || input.indexOf('\0') >= 0)
            throw new IOException("Enter an existing directory path.");
        File candidate;
        if (input.equals("~")) candidate = home;
        else if (input.startsWith("~/")) candidate = new File(home, input.substring(2));
        else {
            candidate = new File(input);
            if (!candidate.isAbsolute()) candidate = new File(home, input);
        }
        File canonical = candidate.getCanonicalFile();
        if (!canonical.isDirectory() || !canonical.canRead())
            throw new IOException("Directory is missing or not readable. For phone storage, grant storage access first.");
        return canonical;
    }

    public static String displayPath(String absolutePath, String homePath) {
        if (absolutePath.equals(homePath)) return "~";
        if (absolutePath.startsWith(homePath + File.separator))
            return "~" + absolutePath.substring(homePath.length());
        return absolutePath;
    }

    /** Do not allow terminal titles or filenames to inject bidi/control characters into app chrome. */
    public static String safeLabel(String value) {
        if (value == null) return "";
        return value.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ");
    }
}
