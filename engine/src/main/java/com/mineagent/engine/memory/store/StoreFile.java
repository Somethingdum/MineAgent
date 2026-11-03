package com.mineagent.engine.memory.store;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * One file of the memory store (SMH plan 70, MA5.7): gzip-compressed, a header line naming the format, what it holds,
 * its version, the body's CRC-32 and length, then the body. A write goes to a temporary file, forced to disk, then
 * renamed over the old one; the old one, if it still reads, is kept first as the last good copy ({@code .bak}). A file
 * that won't read (a bad checksum, cut short) is set aside in {@code quarantine/} and the last good copy is put back,
 * so at most that one file's latest changes are lost. Pure Java.
 */
public final class StoreFile {
    static final String MAGIC = "MASTORE";
    public static final int FORMAT = 1;

    public record Contents(String kind, int version, String body) {}

    /** What reading found: the contents, if any, and whether they came back from the last good copy. */
    public record ReadResult(Optional<Contents> contents, boolean recovered, boolean quarantined) {}

    private StoreFile() {}

    public static byte[] encode(String kind, int version, String body) {
        String k = clean(kind);
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        CRC32 crc = new CRC32();
        crc.update(data);
        String header = MAGIC + " " + FORMAT + " " + k + " " + version + " " + Long.toHexString(crc.getValue()) + " "
                + data.length + "\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length / 4 + 64);
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(header.getBytes(StandardCharsets.UTF_8));
            gz.write(data);
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
        return out.toByteArray();
    }

    public static Contents decode(byte[] bytes) throws IOException {
        byte[] raw;
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            raw = gz.readAllBytes();
        } catch (IOException | RuntimeException broken) {
            throw new IOException("not a whole gzip file: " + broken.getMessage(), broken);
        }
        int nl = -1;
        for (int i = 0; i < Math.min(raw.length, 512); i++) {
            if (raw[i] == '\n') {
                nl = i;
                break;
            }
        }
        if (nl < 0) throw new IOException("no header");
        String[] h = new String(raw, 0, nl, StandardCharsets.UTF_8).split(" ");
        if (h.length != 6 || !MAGIC.equals(h[0])) throw new IOException("not a memory store file");
        try {
            int format = Integer.parseInt(h[1]);
            if (format > FORMAT) throw new IOException("written by a newer version (format " + format + ")");
            int version = Integer.parseInt(h[3]);
            long crc = Long.parseLong(h[4], 16);
            int length = Integer.parseInt(h[5]);
            if (raw.length - nl - 1 != length) throw new IOException("cut short or too long");
            CRC32 check = new CRC32();
            check.update(raw, nl + 1, length);
            if (check.getValue() != crc) throw new IOException("checksum does not match");
            return new Contents(h[2], version, new String(raw, nl + 1, length, StandardCharsets.UTF_8));
        } catch (NumberFormatException bad) {
            throw new IOException("bad header", bad);
        }
    }

    public static Path lastGood(Path file) {
        return file.resolveSibling(file.getFileName() + ".bak");
    }

    /** Write it safely, keeping what was there as the last good copy if it still reads. */
    public static void write(Path file, String kind, int version, String body) throws IOException {
        Files.createDirectories(file.getParent());
        byte[] bytes = encode(kind, version, body);
        if (Files.exists(file)) {
            try {
                decode(Files.readAllBytes(file));
                Files.copy(file, lastGood(file), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException bad) {
                // It doesn't read: the older last good copy stays.
            }
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Read it, or its last good copy if it won't read (and set the bad one aside). */
    public static ReadResult read(Path file) {
        boolean quarantined = false;
        if (Files.exists(file)) {
            try {
                return new ReadResult(Optional.of(decode(Files.readAllBytes(file))), false, false);
            } catch (IOException | RuntimeException bad) {
                quarantine(file);
                quarantined = true;
            }
        }
        Path bak = lastGood(file);
        if (Files.exists(bak)) {
            try {
                Contents c = decode(Files.readAllBytes(bak));
                Files.copy(bak, file, StandardCopyOption.REPLACE_EXISTING);
                return new ReadResult(Optional.of(c), true, quarantined);
            } catch (IOException | RuntimeException alsoBad) {
                quarantine(bak);
            }
        }
        return new ReadResult(Optional.empty(), false, quarantined);
    }

    static void quarantine(Path file) {
        try {
            Path dir = file.getParent().resolve("quarantine");
            Files.createDirectories(dir);
            Files.move(file, dir.resolve(file.getFileName() + "." + System.currentTimeMillis()),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException ignored) {
            // Left where it is: the next write replaces it.
        }
    }

    static String clean(String kind) {
        String k = kind == null || kind.isBlank() ? "data" : kind.strip();
        return k.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
