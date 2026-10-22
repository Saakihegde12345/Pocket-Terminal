/* SPDX-License-Identifier: GPL-3.0-only */
import com.termux.app.studio.WorkspacePaths;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class WorkspacePathsCheck {
    private static int requiredChecks;
    private static int passedChecks;
    private static int skippedChecks;
    private static void passRequired() {
        requiredChecks++;
        passedChecks++;
    }
    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        passRequired();
    }
    private static void optionalCheck(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        passedChecks++;
    }
    private static void rejects(String input, File home) throws Exception {
        try { WorkspacePaths.resolveDirectory(input, home); }
        catch (IOException expected) { passRequired(); return; }
        throw new AssertionError("Accepted invalid directory: " + input);
    }
    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("studio-path-check-");
        try {
            File home = temporary.resolve("home").toFile();
            check(home.mkdir(), "Create test home");
            File odd = new File(home, "project 'with spaces'; $(literal)");
            check(odd.mkdir(), "Create literal special-character directory");
            check(WorkspacePaths.resolveDirectory("~", home).equals(home.getCanonicalFile()), "Expand home");
            check(WorkspacePaths.resolveDirectory("~/" + odd.getName(), home).equals(odd.getCanonicalFile()), "Expand tilde child");
            check(WorkspacePaths.resolveDirectory(odd.getName(), home).equals(odd.getCanonicalFile()), "Resolve relative directory");
            check(WorkspacePaths.resolveDirectory(odd.getPath(), home).getName().equals(odd.getName()), "Preserve spaces, quotes and shell metacharacters as path data");
            check(WorkspacePaths.displayPath(home.getPath(), home.getPath()).equals("~"), "Display home shorthand");
            check(WorkspacePaths.displayPath(odd.getPath(), home.getPath()).equals("~/" + odd.getName()), "Display child shorthand");
            String sibling = home.getPath() + "-other/project";
            check(WorkspacePaths.displayPath(sibling, home.getPath()).equals(sibling), "Respect home path boundary");
            check(WorkspacePaths.safeLabel("a\n\rb\u202Ec").equals("a  b c"), "Strip chrome control and bidi characters");
            check(WorkspacePaths.safeLabel("开发🚀").equals("开发🚀"), "Preserve visible Unicode");
            check(WorkspacePaths.safeLabel(null).isEmpty(), "Handle absent title");
            rejects(null, home);
            rejects("", home);
            rejects("a\0b", home);
            rejects("does-not-exist", home);
            File file = new File(home, "plain-file");
            check(file.createNewFile(), "Create regular file");
            rejects(file.getPath(), home);
            try {
                Path link = temporary.resolve("symlink");
                Files.createSymbolicLink(link, odd.toPath());
                optionalCheck(WorkspacePaths.resolveDirectory(link.toString(), home).equals(odd.getCanonicalFile()), "Canonicalize symlink target");
            } catch (UnsupportedOperationException | java.nio.file.FileSystemException | SecurityException ignored) {
                skippedChecks++;
                System.out.println("Symlink check skipped: symbolic-link creation unavailable (Windows may require Developer Mode or link privilege).");
            }
            System.out.println("Workspace path checks: required=" + requiredChecks + ", passed=" + passedChecks + ", skipped=" + skippedChecks);
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(temporary)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException e) { throw new RuntimeException(e); }
                });
            }
        }
    }
}
