/* SPDX-License-Identifier: GPL-3.0-only */
package com.termux.app.studio;

/** A single-line input template. Never stores or supplies an Enter/control key. */
public final class CommandSnippet {
    public static final int MAX_NAME_LENGTH = 60;
    public static final int MAX_COMMAND_LENGTH = 4096;
    public final String id;
    public final String name;
    public final String command;

    public CommandSnippet(String id, String name, String command) {
        if (id == null || id.isEmpty() || id.length() > 100 || !isNameValid(name) || !isCommandValid(command))
            throw new IllegalArgumentException("Invalid snippet");
        this.id = id;
        this.name = name.trim();
        this.command = command;
    }

    public static boolean isNameValid(String text) {
        return text != null && !text.trim().isEmpty() && text.length() <= MAX_NAME_LENGTH && isSingleLine(text);
    }

    public static boolean isCommandValid(String text) {
        return text != null && !text.trim().isEmpty() && text.length() <= MAX_COMMAND_LENGTH && isSingleLine(text);
    }

    private static boolean isSingleLine(String text) {
        for (int offset = 0; offset < text.length();) {
            int cp = text.codePointAt(offset);
            int type = Character.getType(cp);
            if (Character.isISOControl(cp) || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR)
                return false;
            if (Character.isSurrogate(text.charAt(offset)) && Character.charCount(cp) == 1) return false;
            offset += Character.charCount(cp);
        }
        return true;
    }
}
