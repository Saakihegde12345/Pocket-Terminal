/* SPDX-License-Identifier: GPL-3.0-only */
import com.termux.app.studio.CommandSnippet;
import com.termux.app.studio.OutputSearch;

/** SDK-independent regression checks; runs in the APK workflow as well as locally. */
public final class WorkspaceToolsCheck {
    private static int checks;
    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks++;
    }

    public static void main(String[] args) {
        check(OutputSearch.find("", "x", false).matches.isEmpty(), "Empty output");
        check(OutputSearch.find("anything", "", false).matches.isEmpty(), "Empty search");
        check(OutputSearch.find("a[b] a.b", "[b]", false).matches.size() == 1, "Literal brackets");
        check(OutputSearch.find("a[b] a.b", "a.b", false).matches.get(0).start == 5, "Literal dot");
        check(OutputSearch.find("Error ERROR error", "error", false).matches.size() == 3, "Ignore case");
        check(OutputSearch.find("Error ERROR error", "error", true).matches.size() == 1, "Match case");
        check(OutputSearch.find("İstanbul İstanbul", "istanbul", false).matches.size() == 2, "Unicode case folding");
        String unicode = "👩‍💻 café 日本語 e\u0301";
        OutputSearch.Match japanese = OutputSearch.find(unicode, "日本語", true).matches.get(0);
        check(unicode.substring(japanese.start, japanese.end).equals("日本語"), "UTF-16 offsets after emoji");
        check(OutputSearch.find(unicode, "👩‍💻", false).matches.get(0).end == 5, "Emoji sequence offsets");
        check(OutputSearch.find(unicode, "e\u0301", false).matches.size() == 1, "Combining sequence");
        check(OutputSearch.find("one\ntwo", "e\nt", true).matches.size() == 1, "Multiline output match");
        StringBuilder repeated = new StringBuilder();
        for (int i = 0; i < OutputSearch.MAX_MATCHES + 1; i++) repeated.append('x');
        OutputSearch.Result capped = OutputSearch.find(repeated.toString(), "x", true);
        check(capped.limited && capped.matches.size() == OutputSearch.MAX_MATCHES, "Bound match memory");
        check(!OutputSearch.find(repeated.substring(1), "x", true).limited, "Exact match limit");
        check(OutputSearch.find("abc", repeated.toString(), true).matches.isEmpty(), "Reject oversized query");
        check(OutputSearch.nextIndex(-1, 1, 3) == 0, "First match");
        check(OutputSearch.nextIndex(-1, -1, 3) == 2, "Previous from unselected");
        check(OutputSearch.nextIndex(2, 1, 3) == 0, "Wrap next");
        check(OutputSearch.nextIndex(0, -1, 3) == 2, "Wrap previous");
        check(OutputSearch.nextIndex(0, 1, 0) == -1, "No matches");
        check(OutputSearch.nextIndex(0, -1, 1) == 0, "Single match");
        String command = "  printf '%s' 'hello 日本語 👩‍💻'; git status && ls  ";
        CommandSnippet snippet = new CommandSnippet("one", "  My command  ", command);
        check(snippet.name.equals("My command") && snippet.command.equals(command), "Preserve literal command text");
        for (String invalid : new String[]{"", " ", "pwd\n", "pwd\r", "ls\t-l", "\u001b[31m", "x\u0000", "x\u007f", "x\u0085", "x\u2028", "x\u2029", "x\ud800"})
            check(!CommandSnippet.isCommandValid(invalid), "Reject control, newline, empty or broken Unicode");
        check(!CommandSnippet.isCommandValid(null), "Reject null command");
        check(!CommandSnippet.isNameValid("bad\nname"), "Reject multiline name");
        check(!CommandSnippet.isNameValid(null), "Reject null name");
        StringBuilder maximum = new StringBuilder();
        for (int i = 0; i < CommandSnippet.MAX_COMMAND_LENGTH; i++) maximum.append('a');
        check(CommandSnippet.isCommandValid(maximum.toString()), "Maximum command size");
        check(!CommandSnippet.isCommandValid(maximum + "a"), "Reject oversized command");
        try {
            new CommandSnippet("id", "name", "pwd\n");
            throw new AssertionError("Unsafe snippet constructed");
        } catch (IllegalArgumentException expected) { checks++; }
        Thread.currentThread().interrupt();
        check(OutputSearch.find(repeated.toString(), "x", true).matches.isEmpty(), "Search cancellation");
        Thread.interrupted();
        System.out.println("Workspace tool checks passed: " + checks);
    }
}
