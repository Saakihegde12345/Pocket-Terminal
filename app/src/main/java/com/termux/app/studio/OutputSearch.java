/* SPDX-License-Identifier: GPL-3.0-only */
package com.termux.app.studio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Literal search on an immutable transcript. Offsets refer to the original UTF-16 text. */
public final class OutputSearch {
    public static final int MAX_QUERY_LENGTH = 256;
    public static final int MAX_MATCHES = 2000;

    public static final class Match {
        public final int start;
        public final int end;
        private Match(int start, int end) { this.start = start; this.end = end; }
    }

    public static final class Result {
        public final List<Match> matches;
        public final boolean limited;
        private Result(List<Match> matches, boolean limited) {
            this.matches = Collections.unmodifiableList(matches);
            this.limited = limited;
        }
    }

    private OutputSearch() {}

    public static Result find(String text, String query, boolean matchCase) {
        List<Match> matches = new ArrayList<>();
        if (query.isEmpty() || query.length() > MAX_QUERY_LENGTH)
            return new Result(matches, false);
        int flags = matchCase ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        Matcher matcher = Pattern.compile(Pattern.quote(query), flags).matcher(text);
        while (!Thread.currentThread().isInterrupted() && matcher.find()) {
            if (matches.size() == MAX_MATCHES) return new Result(matches, true);
            matches.add(new Match(matcher.start(), matcher.end()));
        }
        return new Result(matches, false);
    }

    /** Wrap in either direction, including when no match was previously selected. */
    public static int nextIndex(int current, int direction, int count) {
        if (count == 0) return -1;
        if (current < 0) return direction < 0 ? count - 1 : 0;
        return (current + (direction < 0 ? count - 1 : 1)) % count;
    }
}
