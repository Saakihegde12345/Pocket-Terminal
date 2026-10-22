/* SPDX-License-Identifier: GPL-3.0-only */
package com.termux.app.studio;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;

/** Read-only filesystem operations. Call on a worker, never on the Android UI thread. */
public final class WorkspaceFiles {
    public static final int MAX_ENTRIES = 200;
    public static final int MAX_PREVIEW_BYTES = 256 * 1024;
    private static final Comparator<Entry> ORDER = Comparator
        .comparing((Entry entry) -> !entry.directory)
        .thenComparing(entry -> entry.file.getName(), String.CASE_INSENSITIVE_ORDER)
        .thenComparing(entry -> entry.file.getName());

    private WorkspaceFiles() {}

    public static final class Entry {
        public final File file;
        public final boolean directory;
        Entry(File file, boolean directory) { this.file = file; this.directory = directory; }
    }

    public static final class Listing {
        public final File root;
        public final File directory;
        public final List<Entry> entries;
        public final int total;
        Listing(File root, File directory, List<Entry> entries, int total) {
            this.root = root;
            this.directory = directory;
            this.entries = Collections.unmodifiableList(entries);
            this.total = total;
        }
    }

    public static final class Preview {
        public final File file;
        public final String text;
        public final int bytes;
        public final boolean bom;
        private final byte[] fingerprint;
        Preview(File file, String text, byte[] data, boolean bom) {
            this.file = file; this.text = text; this.bytes = data.length; this.bom = bom;
            fingerprint = digest(data);
        }
    }

    /** Resolve links before checking a path boundary; sibling names are not descendants. */
    public static File resolve(File root, File target) throws IOException {
        File base = root.getCanonicalFile();
        File file = target.getCanonicalFile();
        String prefix = base.getPath();
        if (!prefix.endsWith(File.separator)) prefix += File.separator;
        if (!file.equals(base) && !file.getPath().startsWith(prefix))
            throw new IOException("This path leads outside this project. Open its directory as a separate project.");
        return file;
    }

    private static void checkCancelled() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("File loading cancelled.");
    }

    public static Listing list(File root, File requested, boolean showHidden, String query) throws IOException {
        checkCancelled();
        File base = root.getCanonicalFile();
        File directory = resolve(base, requested);
        if (!directory.isDirectory() || !directory.canRead()) throw new IOException("Folder is missing or not readable.");
        String[] names = directory.list();
        if (names == null) throw new IOException("Folder is missing or not readable.");
        String term = query.toLowerCase(Locale.ROOT);
        // Retain only the first 200 sorted entries, including when a directory is very large.
        PriorityQueue<Entry> selected = new PriorityQueue<>(MAX_ENTRIES, ORDER.reversed());
        int total = 0;
        for (String name : names) {
            checkCancelled();
            if ((!showHidden && name.startsWith(".")) || !name.toLowerCase(Locale.ROOT).contains(term)) continue;
            File file = new File(directory, name);
            Entry entry = new Entry(file, file.isDirectory());
            total++;
            if (selected.size() < MAX_ENTRIES) selected.add(entry);
            else if (ORDER.compare(entry, selected.peek()) < 0) { selected.poll(); selected.add(entry); }
        }
        List<Entry> entries = new ArrayList<>(selected);
        entries.sort(ORDER);
        return new Listing(base, directory, entries, total);
    }

    public static Preview preview(File root, File requested) throws IOException {
        checkCancelled();
        File file = resolve(root, requested);
        if (!file.isFile() || !file.canRead()) throw new IOException("File is missing or not readable.");
        if (file.length() > MAX_PREVIEW_BYTES) throw new IOException("Preview supports text files up to 256 KiB.");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                checkCancelled();
                // The file may have grown after the size check.
                if (bytes.size() + count > MAX_PREVIEW_BYTES)
                    throw new IOException("Preview supports text files up to 256 KiB.");
                bytes.write(buffer, 0, count);
            }
        }
        byte[] data = bytes.toByteArray();
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException e) {
            throw new IOException("Preview supports UTF-8 text. This file uses another encoding or contains binary data.");
        }
        boolean bom = text.startsWith("\uFEFF");
        if (bom) text = text.substring(1);
        for (int i = 0; i < text.length(); i++) {
            if ((i & 4095) == 0) checkCancelled();
            char c = text.charAt(i);
            if ((c < 32 && c != '\n' && c != '\r' && c != '\t') || (c >= 127 && c <= 159))
                throw new IOException("This file contains binary or control data and cannot be previewed.");
        }
        return new Preview(file, text, data, bom);
    }

    /** Recheck the original bytes, including BOM, before replacing an edited file. */
    public static byte[] prepareSave(File root, Preview original, String text) throws IOException {
        checkCancelled();
        Preview current = preview(root, original.file);
        if (!Arrays.equals(current.fingerprint, original.fingerprint))
            throw new IOException("The file changed on disk. Reload it before saving your edits.");
        byte[] data;
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .encode(java.nio.CharBuffer.wrap((original.bom ? "\uFEFF" : "") + text));
            data = new byte[encoded.remaining()];
            encoded.get(data);
        } catch (CharacterCodingException e) { throw new IOException("Text contains an incomplete Unicode character."); }
        if (data.length > MAX_PREVIEW_BYTES) throw new IOException("Editing supports UTF-8 files up to 256 KiB.");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c < 32 && c != '\n' && c != '\r' && c != '\t') || (c >= 127 && c <= 159))
                throw new IOException("Remove binary or control characters before saving.");
        }
        return data;
    }

    private static byte[] digest(byte[] data) {
        try { return MessageDigest.getInstance("SHA-256").digest(data); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
