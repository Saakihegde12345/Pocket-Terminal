/* SPDX-License-Identifier: GPL-3.0-only */
import com.termux.app.studio.HostProfile;
import com.termux.app.studio.PackageEntry;
import com.termux.app.studio.TerminalTheme;
import java.util.Properties;
import java.util.Arrays;

public final class WorkspaceFeaturesCheck {
    private static int checks;
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label); checks++;
    }
    private static void rejects(Runnable operation) {
        try { operation.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Accepted invalid input");
    }
    public static void main(String[] args) {
        HostProfile host = new HostProfile("test", "Work", "work.example.org", "dev", 2222);
        check(Arrays.equals(host.arguments(false), new String[]{"-p", "2222", "--", "dev@work.example.org"}), "SSH uses separate arguments");
        check(Arrays.equals(host.arguments(true), new String[]{"-P", "2222", "--", "dev@work.example.org"}), "SFTP port flag differs");
        HostProfile ipv6 = new HostProfile("ipv6", "IPv6", "::1", "dev", 22);
        check(ipv6.arguments(true)[3].equals("dev@[::1]"), "SFTP brackets IPv6 literals");
        check(ipv6.arguments(false)[3].equals("dev@::1"), "SSH receives IPv6 without SFTP syntax");
        for (String invalid : new String[]{"-oProxyCommand=x", "host;touch /tmp/x", "host\ncommand", "$(id)", "host/user", "", "bad..host"})
            rejects(() -> new HostProfile("id", "Name", invalid, "user", 22));
        for (String invalid : new String[]{"-oOption", "user@host", "user name", "$(id)", "", "u\n"})
            rejects(() -> new HostProfile("id", "Name", "host", invalid, 22));
        rejects(() -> new HostProfile("id", "Name", "host", "user", 0));
        rejects(() -> new HostProfile("id", "Name", "host", "user", 65536));
        rejects(() -> new HostProfile("id", "Name\u202e", "host", "user", 22));
        check(new HostProfile("id", "日本語", "127.0.0.1", "user", 65535).port == 65535, "Maximum valid port");
        PackageEntry entry = PackageEntry.parse("openssh - Secure shell client and server");
        check(entry.name.equals("openssh"), "Package identifier separated from description");
        check(entry.description.equals("Secure shell client and server"), "Package description preserved");
        check(new PackageEntry("libc++", "C++ runtime").name.equals("libc++"), "Valid package punctuation");
        for (String invalid : new String[]{"--option", "x;pwd", "x y", "x\n", "$(id)", ""})
            rejects(() -> new PackageEntry(invalid, "description"));
        rejects(() -> PackageEntry.parse("E: No index"));
        check(PackageEntry.searchExpression("libc++").equals("libc\\+\\+"), "APT query escapes POSIX regex metacharacters");
        check(PackageEntry.searchExpression("[a].*").equals("\\[a\\]\\.\\*"), "Query remains literal");
        check(PackageEntry.searchExpression("").equals("."), "Empty query lists the catalog");
        rejects(() -> PackageEntry.searchExpression("a\ncommand"));
        for (int i = 0; i < TerminalTheme.PRESETS.length; i++) {
            Properties colors = TerminalTheme.validate(TerminalTheme.preset(i));
            check(colors.size() == 19, "Preset contains complete palette");
            check(!colors.getProperty("foreground").equals(colors.getProperty("background")), "Readable preset");
        }
        Properties colors = TerminalTheme.preset(0);
        colors.setProperty("cursor", "#ABCDEF");
        check(TerminalTheme.validate(colors).getProperty("cursor").equals("#abcdef"), "Hex colors normalized");
        colors.setProperty("color15", "#fff"); rejects(() -> TerminalTheme.validate(colors));
        colors.remove("color15"); rejects(() -> TerminalTheme.validate(colors));
        rejects(() -> TerminalTheme.preset(-1));
        System.out.println("Workspace feature checks passed: " + checks);
    }
}
