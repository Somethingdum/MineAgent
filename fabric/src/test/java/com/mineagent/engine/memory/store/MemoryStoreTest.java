package com.mineagent.engine.memory.store;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 70, MA5.7: the memory store keeps what it is given through bad files, crashes and a small memory. */
final class MemoryStoreTest {
    @Test void aFileComesBackAsWritten() throws Exception {
        Path dir = Files.createTempDirectory("store");
        Path f = dir.resolve("a/b.gz");
        StoreFile.write(f, "places", 3, "{\"x\":\"é\"}");
        var r = StoreFile.read(f);
        assertEquals("places", r.contents().orElseThrow().kind());
        assertEquals(3, r.contents().get().version());
        assertEquals("{\"x\":\"é\"}", r.contents().get().body());
        assertFalse(r.recovered());
    }

    @Test void aDamagedFileIsSetAsideAndTheLastGoodCopyComesBack() throws Exception {
        Path dir = Files.createTempDirectory("store");
        Path f = dir.resolve("t.gz");
        StoreFile.write(f, "t", 1, "first");
        StoreFile.write(f, "t", 1, "second"); // "first" is now the last good copy
        byte[] bytes = Files.readAllBytes(f);
        bytes[bytes.length / 2] ^= 0x55; // damage it
        Files.write(f, bytes);
        var r = StoreFile.read(f);
        assertTrue(r.quarantined());
        assertTrue(r.recovered());
        assertEquals("first", r.contents().orElseThrow().body());
        assertTrue(Files.list(dir.resolve("quarantine")).findAny().isPresent());
        assertEquals("first", StoreFile.read(f).contents().orElseThrow().body(), "the good copy is put back in place");
    }

    @Test void aJournalKeepsWholeLinesAndDropsATornOne() throws Exception {
        Path dir = Files.createTempDirectory("store");
        Path f = dir.resolve("j.log");
        try (JournalFile j = new JournalFile(f)) {
            j.append("one");
            j.append("two");
        }
        Files.write(f, "9a1b2c3d\tthr".getBytes(StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.APPEND);
        assertEquals(java.util.List.of("one", "two"), JournalFile.read(f));
    }

    /** A table of names to values, one region per first letter, at most two regions in memory. */
    static final class Letters extends RegionTable<TreeMap<String, String>> {
        Letters(MemoryStore store) {
            super(store, "letters", 2);
            if (store != null) store.register(this);
        }

        private static RegionKey key(String k) {
            return new RegionKey("test:dim", k.charAt(0), 0);
        }

        void put(String k, String v) {
            synchronized (this) {
                region(key(k)).put(k, v);
                touched(key(k));
            }
            if (store != null) store.log(name(), "put", k + "=" + v);
        }

        String get(String k) {
            synchronized (this) {
                return region(key(k)).get(k);
            }
        }

        int loaded() {
            return loadedCount();
        }

        @Override protected TreeMap<String, String> empty() { return new TreeMap<>(); }

        @Override protected String encode(TreeMap<String, String> r) {
            StringBuilder sb = new StringBuilder();
            r.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
            return sb.toString();
        }

        @Override protected TreeMap<String, String> decode(String body) {
            TreeMap<String, String> r = new TreeMap<>();
            for (String line : body.split("\n")) {
                int eq = line.indexOf('=');
                if (eq > 0) r.put(line.substring(0, eq), line.substring(eq + 1));
            }
            return r;
        }

        @Override public void replay(String op, String json) {
            int eq = json.indexOf('=');
            synchronized (this) {
                region(key(json.substring(0, eq))).put(json.substring(0, eq), json.substring(eq + 1));
                touched(key(json.substring(0, eq)));
            }
        }

        @Override public long count() { return 0; }
    }

    @Test void afterACrashTheJournalPutsBackWhatWasNotYetWritten() throws Exception {
        Path dir = Files.createTempDirectory("store");
        MemoryStore s1 = new MemoryStore(dir);
        Letters t1 = new Letters(s1);
        t1.put("apple", "1");
        s1.flushBlocking();
        t1.put("banana", "2"); // only in the journal: no flush, no close (a crash)
        MemoryStore s2 = new MemoryStore(dir);
        Letters t2 = new Letters(s2);
        assertEquals(1, s2.replayJournals());
        assertEquals("1", t2.get("apple"));
        assertEquals("2", t2.get("banana"));
        s2.close();
        MemoryStore s3 = new MemoryStore(dir);
        Letters t3 = new Letters(s3);
        assertEquals(0, s3.replayJournals(), "the replayed changes were written and that journal removed");
        assertEquals("2", t3.get("banana"));
        s3.close();
    }

    @Test void regionsNotInUseLeaveMemoryAndComeBackFromDisk() throws Exception {
        Path dir = Files.createTempDirectory("store");
        MemoryStore s = new MemoryStore(dir);
        Letters t = new Letters(s);
        for (String w : new String[] {"ant", "bee", "cat", "dog", "eel"}) t.put(w, w.toUpperCase());
        assertTrue(t.loaded() <= 2, "only the regions in use stay in memory");
        s.flushBlocking();
        assertEquals("ANT", t.get("ant"));
        assertEquals("EEL", t.get("eel"));
        var report = s.report();
        assertTrue(report.files() >= 5, "a file per region");
        assertTrue(report.bytesOnDisk() > 0);
        s.close();
    }

    @Test void withNoStoreItAllStaysInMemory() {
        Letters t = new Letters(null);
        for (String w : new String[] {"ant", "bee", "cat", "dog"}) t.put(w, w);
        assertEquals(4, t.loaded());
        assertEquals("cat", t.get("cat"));
    }
}
