package com.mineagent.engine.memory.store;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * An append-only log of lines, each with its own CRC-32 (SMH plan 70, MA5.7): the memory store's changes between
 * writes, and the journal of what happened. Reading keeps every line whose checksum holds; a line cut short when the
 * game or the PC stopped mid-write is dropped. Pure Java.
 */
public final class JournalFile implements Closeable {
    private final Path file;
    private FileChannel channel;

    public JournalFile(Path file) throws IOException {
        this.file = file;
        Files.createDirectories(file.getParent());
        this.channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.APPEND);
    }

    public Path path() {
        return file;
    }

    /** One line (no line breaks in it), with its checksum. */
    public synchronized void append(String line) throws IOException {
        if (channel == null) throw new IOException("journal closed");
        String clean = line.replace('\n', ' ').replace('\r', ' ');
        byte[] data = clean.getBytes(StandardCharsets.UTF_8);
        CRC32 crc = new CRC32();
        crc.update(data);
        byte[] record = (Long.toHexString(crc.getValue()) + "\t" + clean + "\n").getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.wrap(record);
        while (buffer.hasRemaining()) channel.write(buffer);
    }

    /** Onto the disk. */
    public synchronized void sync() throws IOException {
        if (channel != null) channel.force(false);
    }

    public synchronized long size() throws IOException {
        return channel == null ? Files.size(file) : channel.size();
    }

    @Override
    public synchronized void close() throws IOException {
        if (channel != null) {
            channel.force(false);
            channel.close();
            channel = null;
        }
    }

    /** The lines whose checksums hold, in order. */
    public static List<String> read(Path file) throws IOException {
        List<String> out = new ArrayList<>();
        if (!Files.exists(file)) return out;
        String all = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        for (String record : all.split("\n", -1)) {
            int tab = record.indexOf('\t');
            if (tab <= 0) continue;
            String line = record.substring(tab + 1);
            try {
                long crc = Long.parseLong(record.substring(0, tab), 16);
                CRC32 check = new CRC32();
                check.update(line.getBytes(StandardCharsets.UTF_8));
                if (check.getValue() == crc) out.add(line);
            } catch (NumberFormatException torn) {
                // A torn line: dropped.
            }
        }
        return out;
    }
}
