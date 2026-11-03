package com.mineagent.engine.memory.store;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * The companion's bigger memory (SMH plan 70, MA5.7), in the world's folder: tables of what it remembers, each kept
 * as small files ({@link StoreFile}: compressed, versioned, checksummed, with a last good copy), the big ones split by
 * 512-block region and loaded only when needed. Every change is also appended to a write-ahead journal ({@link
 * JournalFile}) the moment it happens, and the files are written every so often, only those that changed, on a
 * background thread so the game never waits on the disk. After a crash the journal is replayed onto the last files
 * written. Pure Java.
 */
public final class MemoryStore implements Closeable {
    /** A table of the store: what it holds, how it takes back a journal line, what changed since it was last written. */
    public interface Table {
        String name();

        /** Apply one journal line ({@link #log}) again after a restart. Must not log. */
        void replay(String op, String json);

        /** The files that changed since the last write (relative path to contents); after this, nothing is changed. */
        Map<String, Segment> takeDirty();

        /** How many things it holds, for the report. */
        long count();
    }

    public record Segment(String kind, int version, String body) {}

    /** Counts and sizes only, never what is in them. */
    public record Report(Map<String, Long> counts, long bytesOnDisk, int files, int recovered, int quarantined,
                         int journalLinesReplayed) {}

    private final Path root;
    private final List<Table> tables = new CopyOnWriteArrayList<>();
    private final ExecutorService writer;
    private final AtomicInteger recovered = new AtomicInteger();
    private final AtomicInteger quarantined = new AtomicInteger();
    private int replayed;
    private JournalFile wal;
    private int walIndex;
    private volatile boolean closed;
    private java.util.concurrent.ScheduledExecutorService ticker;

    public MemoryStore(Path root) throws IOException {
        this.root = root;
        Files.createDirectories(root);
        this.writer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "MineAgent memory writer");
            t.setDaemon(true);
            return t;
        });
        walIndex = oldJournals().stream().mapToInt(MemoryStore::journalIndex).max().orElse(0) + 1;
        wal = new JournalFile(root.resolve("journal." + walIndex + ".log"));
    }

    public Path root() {
        return root;
    }

    public void register(Table table) {
        tables.add(table);
    }

    /** Read one file of a table; empty if it isn't there or nothing good of it is left. */
    public Optional<String> read(String relative, String kind) {
        StoreFile.ReadResult r = StoreFile.read(root.resolve(relative));
        if (r.recovered()) recovered.incrementAndGet();
        if (r.quarantined()) quarantined.incrementAndGet();
        return r.contents().filter(c -> c.kind().equals(StoreFile.clean(kind))).map(StoreFile.Contents::body);
    }

    /** A change, into the journal now (one line of JSON, no line breaks). */
    public synchronized void log(String table, String op, String json) {
        if (closed) return;
        try {
            wal.append(table + " " + op + " " + json);
        } catch (IOException e) {
            System.err.println("[MineAgent] Memory journal write failed: " + e.getMessage());
        }
    }

    /**
     * After a restart: every journal line left from before, applied again to the tables (registered first), then
     * written out, and those journals removed.
     */
    public synchronized int replayJournals() {
        int lines = 0;
        List<Path> old = new ArrayList<>(oldJournals());
        old.removeIf(p -> p.equals(wal.path()));
        old.sort((a, b) -> Integer.compare(journalIndex(a), journalIndex(b)));
        for (Path p : old) {
            try {
                for (String line : JournalFile.read(p)) {
                    int a = line.indexOf(' '), b = a < 0 ? -1 : line.indexOf(' ', a + 1);
                    if (a <= 0 || b <= a) continue;
                    String table = line.substring(0, a), op = line.substring(a + 1, b), json = line.substring(b + 1);
                    for (Table t : tables) {
                        if (t.name().equals(table)) {
                            try {
                                t.replay(op, json);
                                lines++;
                            } catch (RuntimeException bad) {
                                // One bad line costs that change only.
                            }
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("[MineAgent] Memory journal unreadable: " + e.getMessage());
            }
        }
        replayed += lines;
        if (!old.isEmpty()) {
            flushBlocking();
            for (Path p : old) {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // Replayed again next time: the changes are put, not added.
                }
            }
        }
        return lines;
    }

    /**
     * Write what changed, in the background. The journal is started afresh first, so a change made while the files
     * are being written goes to the new one; the old one is removed once every file in this write is safely down.
     */
    public synchronized void flush() {
        if (closed) return;
        JournalFile done = wal;
        try {
            walIndex++;
            wal = new JournalFile(root.resolve("journal." + walIndex + ".log"));
        } catch (IOException e) {
            System.err.println("[MineAgent] Memory journal could not start: " + e.getMessage());
            wal = done;
            return;
        }
        List<Map.Entry<String, Segment>> writes = new ArrayList<>();
        for (Table t : tables) {
            try {
                writes.addAll(t.takeDirty().entrySet());
            } catch (RuntimeException e) {
                System.err.println("[MineAgent] Memory table " + t.name() + " could not be saved: " + e.getMessage());
            }
        }
        writer.submit(() -> {
            boolean ok = true;
            for (var w : writes) {
                try {
                    StoreFile.write(root.resolve(w.getKey()), w.getValue().kind(), w.getValue().version(), w.getValue().body());
                } catch (IOException | RuntimeException e) {
                    ok = false;
                    System.err.println("[MineAgent] Memory file write failed (" + w.getKey() + "): " + e.getMessage());
                }
            }
            try {
                done.close();
                // Kept if a write failed: the next start replays it onto what did get written.
                if (ok) Files.deleteIfExists(done.path());
            } catch (IOException ignored) {
                // Replayed next start.
            }
        });
    }

    /** Write what changed every so often, from a thread of its own. */
    public synchronized void startAutoFlush(long seconds) {
        if (ticker != null || closed) return;
        ticker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MineAgent memory timer");
            t.setDaemon(true);
            return t;
        });
        ticker.scheduleWithFixedDelay(() -> {
            try {
                flush();
            } catch (RuntimeException e) {
                System.err.println("[MineAgent] Memory write failed: " + e.getMessage());
            }
        }, seconds, seconds, TimeUnit.SECONDS);
    }

    /** Write what changed and wait until it is on disk. */
    public void flushBlocking() {
        flush();
        try {
            writer.submit(() -> { }).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("[MineAgent] Memory write did not finish: " + e.getMessage());
        }
    }

    public Report report() {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (Table t : tables) counts.put(t.name(), t.count());
        long bytes = 0;
        int files = 0;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                try {
                    bytes += Files.size(p);
                    files++;
                } catch (IOException ignored) {
                    // Gone since the walk began.
                }
            }
        } catch (IOException ignored) {
            // Nothing to count.
        }
        return new Report(counts, bytes, files, recovered.get(), quarantined.get(), replayed);
    }

    @Override
    public void close() {
        if (closed) return;
        synchronized (this) {
            if (ticker != null) ticker.shutdownNow();
        }
        flushBlocking();
        synchronized (this) {
            closed = true;
            try {
                wal.close();
                if (Files.size(wal.path()) == 0) Files.deleteIfExists(wal.path());
            } catch (IOException ignored) {
                // Replayed next start.
            }
        }
        writer.shutdown();
        try {
            writer.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private List<Path> oldJournals() {
        try (Stream<Path> list = Files.list(root)) {
            return list.filter(p -> journalIndex(p) > 0).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static int journalIndex(Path p) {
        String n = p.getFileName().toString();
        if (!n.startsWith("journal.") || !n.endsWith(".log")) return -1;
        try {
            return Integer.parseInt(n.substring(8, n.length() - 4));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
