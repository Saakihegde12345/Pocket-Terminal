/* SPDX-License-Identifier: GPL-3.0-only */
import com.termux.app.studio.WorkspaceFiles;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class WorkspaceFilesCheck {
    private static int checks;
    private interface Operation { void run() throws Exception; }
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
    }
    private static void rejects(Operation operation, String label) throws Exception {
        try { operation.run(); }
        catch (IOException expected) { checks++; return; }
        throw new AssertionError(label);
    }
    private static File write(Path root, String name, byte[] data) throws Exception {
        return Files.write(root.resolve(name), data).toFile();
    }
    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("workspace-files-check-");
        try {
            Path project = Files.createDirectory(temp.resolve("project"));
            File root = project.toFile();
            File folder = Files.createDirectory(project.resolve("z-folder")).toFile();
            String content = "// 开发 🚀\r\n  const name = '$literal';\n\treturn name;\n";
            File code = write(project, "a 'file'; $(literal).js", content.getBytes(StandardCharsets.UTF_8));
            write(project, ".env", "LOCAL_ONLY=value".getBytes(StandardCharsets.UTF_8));
            File empty = write(project, "empty.txt", new byte[0]);
            WorkspaceFiles.Listing listing = WorkspaceFiles.list(root, root, false, "");
            check(listing.total == 3, "Hidden files excluded by default");
            check(listing.entries.get(0).directory, "Folders sorted before files");
            check(listing.entries.get(0).file.equals(folder), "Folder entry retains exact path");
            check(listing.entries.get(1).file.equals(code), "Quotes and shell syntax stay literal path data");
            check(WorkspaceFiles.list(root, root, true, "").total == 4, "Hidden files can be included");
            check(WorkspaceFiles.list(root, root, true, ".ENV").entries.get(0).file.getName().equals(".env"), "Case-insensitive filename filter");
            check(WorkspaceFiles.list(root, root, false, "missing").entries.isEmpty(), "No-match filter is empty");
            check(WorkspaceFiles.list(root, folder, false, "").entries.isEmpty(), "Empty folder supported");
            WorkspaceFiles.Preview preview = WorkspaceFiles.preview(root, code);
            check(preview.text.equals(content), "Unicode, tabs and CRLF preserved exactly");
            check(preview.bytes == content.getBytes(StandardCharsets.UTF_8).length, "Byte count counts UTF-8 bytes");
            check(preview.file.equals(code.getCanonicalFile()), "Preview returns canonical file identity");
            check(WorkspaceFiles.preview(root, empty).text.isEmpty(), "Empty text file supported");
            File bom = write(project, "bom.txt", "\uFEFFhello".getBytes(StandardCharsets.UTF_8));
            check(WorkspaceFiles.preview(root, bom).text.equals("hello"), "UTF-8 BOM handled");
            check(new String(Files.readAllBytes(bom.toPath()), StandardCharsets.UTF_8).equals("\uFEFFhello"), "Preview does not modify the source");
            File badUtf8 = write(project, "bad.bin", new byte[]{(byte) 0xc3, 0x28});
            rejects(() -> WorkspaceFiles.preview(root, badUtf8), "Malformed UTF-8 rejected, not replaced");
            File binary = write(project, "nul.bin", new byte[]{'a', 0, 'b'});
            rejects(() -> WorkspaceFiles.preview(root, binary), "Binary NUL rejected");
            File escape = write(project, "escape.txt", "\u001b[31mtest".getBytes(StandardCharsets.UTF_8));
            rejects(() -> WorkspaceFiles.preview(root, escape), "Terminal control bytes rejected");
            File del = write(project, "control.txt", new byte[]{127});
            rejects(() -> WorkspaceFiles.preview(root, del), "DEL control rejected");
            File maximum = write(project, "max.txt", new byte[WorkspaceFiles.MAX_PREVIEW_BYTES]);
            byte[] text = new byte[WorkspaceFiles.MAX_PREVIEW_BYTES];
            java.util.Arrays.fill(text, (byte) 'a');
            Files.write(maximum.toPath(), text);
            check(WorkspaceFiles.preview(root, maximum).bytes == WorkspaceFiles.MAX_PREVIEW_BYTES, "Exact preview byte limit accepted");
            File large = write(project, "too-large.txt", new byte[WorkspaceFiles.MAX_PREVIEW_BYTES + 1]);
            rejects(() -> WorkspaceFiles.preview(root, large), "Oversized file rejected");
            rejects(() -> WorkspaceFiles.preview(root, folder), "Directories not previewed as files");
            rejects(() -> WorkspaceFiles.preview(root, new File(root, "absent")), "Missing file rejected");
            rejects(() -> WorkspaceFiles.list(root, code, false, ""), "Files not listed as directories");
            rejects(() -> WorkspaceFiles.list(root, new File(root, "absent"), false, ""), "Missing directory rejected");
            rejects(() -> WorkspaceFiles.resolve(root, temp.toFile()), "Parent escape rejected");
            Path sibling = Files.createDirectory(temp.resolve("project-other"));
            rejects(() -> WorkspaceFiles.resolve(root, sibling.toFile()), "Sibling prefix is not contained");
            check(WorkspaceFiles.resolve(new File(File.separator), root).equals(root.getCanonicalFile()), "Filesystem-root path boundary works");
            check(WorkspaceFiles.resolve(root, new File(folder, "..")).equals(root.getCanonicalFile()), "Internal parent navigation allowed");
            Path many = Files.createDirectory(project.resolve("many"));
            for (int i = 219; i >= 0; i--) write(many, String.format(java.util.Locale.ROOT, "file-%03d", i), new byte[0]);
            WorkspaceFiles.Listing bounded = WorkspaceFiles.list(root, many.toFile(), false, "");
            check(bounded.total == 220, "Total counts entries beyond display limit");
            check(bounded.entries.size() == WorkspaceFiles.MAX_ENTRIES, "Visible entries bounded");
            check(bounded.entries.get(0).file.getName().equals("file-000"), "Bounded selection keeps first sorted entry");
            check(bounded.entries.get(199).file.getName().equals("file-199"), "Bounded selection deterministic despite enumeration order");
            check(WorkspaceFiles.list(root, many.toFile(), false, "219").total == 1, "Filtering finds names beyond initial visible cap");
            try { bounded.entries.clear(); throw new AssertionError("Mutable detached listing"); }
            catch (UnsupportedOperationException expected) { checks++; }
            try {
                Path outside = Files.write(sibling.resolve("outside.txt"), "outside".getBytes(StandardCharsets.UTF_8));
                File link = Files.createSymbolicLink(project.resolve("outside-link"), outside).toFile();
                rejects(() -> WorkspaceFiles.preview(root, link), "Symlink outside project rejected");
                File folderLink = Files.createSymbolicLink(project.resolve("outside-folder"), sibling).toFile();
                rejects(() -> WorkspaceFiles.list(root, folderLink, true, ""), "Directory symlink outside project rejected");
                File inside = Files.createSymbolicLink(project.resolve("inside-link"), code.toPath()).toFile();
                check(WorkspaceFiles.preview(root, inside).text.equals(content), "Internal symlink preview supported");
            } catch (UnsupportedOperationException | java.nio.file.FileSystemException e) {
                System.out.println("Symlink checks unavailable on this filesystem.");
            }
            Thread.currentThread().interrupt();
            try {
                try { WorkspaceFiles.list(root, root, true, ""); throw new AssertionError("Uncancelled listing"); }
                catch (InterruptedIOException expected) { checks++; }
                try { WorkspaceFiles.preview(root, code); throw new AssertionError("Uncancelled preview"); }
                catch (InterruptedIOException expected) { checks++; }
            } finally { Thread.interrupted(); }
            System.out.println("Workspace file checks passed: " + checks);
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(temp)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException e) { throw new RuntimeException(e); }
                });
            }
        }
    }
}
