/* SPDX-License-Identifier: GPL-3.0-only */
package com.termux.app.studio;

import java.util.Locale;
import java.util.Properties;

/** The default foreground/background/cursor and the complete sixteen-color ANSI palette. */
public final class TerminalTheme {
    public static final String[] KEYS = {"background", "foreground", "cursor", "color0", "color1", "color2",
        "color3", "color4", "color5", "color6", "color7", "color8", "color9", "color10", "color11", "color12", "color13", "color14", "color15"};
    public static final String[] PRESETS = {"Pocket dark", "Midnight", "Paper"};
    private static final String[][] COLORS = {
        {"#0b0e0d", "#ebf0ed", "#c6f868", "#151b17", "#ed6a6a", "#a8d86f", "#e4c76b", "#80bfff", "#c59af2", "#73d9cf", "#d9e0dc", "#65756c", "#ff8585", "#c6f868", "#ffe28c", "#a4d4ff", "#ddbcff", "#9cf1e7", "#ffffff"},
        {"#101522", "#dce4ff", "#80dcff", "#202636", "#f7768e", "#9ece6a", "#e0af68", "#7aa2f7", "#bb9af7", "#7dcfff", "#c0caf5", "#565f89", "#ff9eac", "#b9f27c", "#ffd58b", "#a4c0ff", "#d7baff", "#a4edff", "#ffffff"},
        {"#faf7f0", "#262c30", "#3c556e", "#262c30", "#a63437", "#37652c", "#825c16", "#275e99", "#764c91", "#216c70", "#b7b6b0", "#687078", "#c34447", "#4b7f3e", "#996f20", "#3f76b0", "#9065aa", "#34868a", "#ffffff"}
    };
    private TerminalTheme() {}

    public static Properties preset(int index) {
        if (index < 0 || index >= COLORS.length) throw new IllegalArgumentException("Unknown terminal preset.");
        Properties result = new Properties();
        for (int i = 0; i < KEYS.length; i++) result.setProperty(KEYS[i], COLORS[index][i]);
        return result;
    }

    public static Properties validate(Properties properties) {
        Properties result = new Properties();
        for (String key : KEYS) {
            String color = properties.getProperty(key, "").trim();
            if (!color.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException(key + " needs a color such as #c6f868.");
            result.setProperty(key, color.toLowerCase(Locale.ROOT));
        }
        return result;
    }
}
