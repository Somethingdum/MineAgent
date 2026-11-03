package com.mineagent.engine.memory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mineagent.engine.memory.store.JournalFile;
import com.mineagent.engine.memory.store.MemoryStore;
import com.mineagent.engine.memory.store.StoreFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The journal of what happened in this world (SMH plan 70, MA5.9): your orders and its answers, goals, fights,
 * deaths, finds, places, what it stored where. One log a day in the memory store's folder ({@code events/}), each line
 * with its own checksum, a day compressed once it is over; kept in full for {@code keepDays} (90 by default), and a
 * summary of every day kept for good. Answers "what did we do yesterday?" and "where did you put the iron?". It stays
 * on your PCs. Pure Java.
 */
public final class EventJournal implements MemoryStore.Table {
    public record Event(long at, long tick, String kind, String text, String dim, int x, int y, int z, boolean placed) {}

    /** A day's summary: how many of each kind, and the lines that matter most. */
    public record Day(String date, Map<String, Integer> counts, List<String> highlights) {}

    /** Kinds worth a line in a day's summary. */
    static final List<String> HIGHLIGHT = List.of("death", "find", "place", "goal", "stored", "fight");
    static final int HIGHLIGHTS_PER_DAY = 24;
    static final int RECENT = 200;
    private static final Gson GSON = new Gson();

    private final Clock clock;
    private final ZoneId zone;
    private final ArrayDeque<Event> recent = new ArrayDeque<>();
    private final TreeMap<String, Day> days = new TreeMap<>();
    private Path dir;
    private JournalFile today;
    private String todayDate;
    private int keepDays = 90;
    private boolean summariesDirty;
    private long total;

    public EventJournal() {
        this(Clock.systemDefaultZone());
    }

    public EventJournal(Clock clock) {
        this.clock = clock;
        this.zone = clock.getZone();
    }

    @Override
    public String name() {
        return "events";
    }

    /** Keep it in the memory store's folder, for {@code keepDays} in full. */
    public synchronized void attach(MemoryStore store, int keepDays) {
        this.keepDays = Math.max(1, keepDays);
        this.dir = store.root().resolve("events");
        store.read("events/summaries.gz", "event-days").ifPresent(body -> {
            List<Day> list = GSON.fromJson(body, new TypeToken<List<Day>>() {}.getType());
            if (list != null) {
                for (Day d : list) {
                    if (d != null && d.date() != null && d.counts() != null && d.highlights() != null) days.put(d.date(), d);
                }
            }
        });
        total = days.values().stream().flatMap(d -> d.counts().values().stream()).mapToLong(Integer::longValue).sum();
        String date = LocalDate.now(clock).toString();
        for (Event e : read(date)) recent.addLast(e);
        while (recent.size() > RECENT) recent.removeFirst();
        compressPastDays(date);
        prune();
        store.register(this);
    }

    // ── Writing ────────────────────────────────────────────────────

    public void record(String kind, String text, long tick) {
        record(kind, text, tick, null, 0, 0, 0, false);
    }

    public void record(String kind, String text, long tick, String dim, int x, int y, int z) {
        record(kind, text, tick, dim, x, y, z, true);
    }

    private synchronized void record(String kind, String text, long tick, String dim, int x, int y, int z, boolean placed) {
        if (kind == null || text == null || text.isBlank()) return;
        String t = text.strip();
        if (t.length() > 300) t = t.substring(0, 300) + "…";
        Event e = new Event(clock.millis(), tick, kind, t, dim, x, y, z, placed);
        recent.addLast(e);
        while (recent.size() > RECENT) recent.removeFirst();
        String date = dateOf(e.at());
        Day d = days.computeIfAbsent(date, k -> new Day(k, new LinkedHashMap<>(), new ArrayList<>()));
        d.counts().merge(kind, 1, Integer::sum);
        if (HIGHLIGHT.contains(kind) && d.highlights().size() < HIGHLIGHTS_PER_DAY) d.highlights().add(t);
        summariesDirty = true;
        total++;
        if (dir == null) return;
        try {
            if (!date.equals(todayDate)) {
                if (today != null) today.close();
                compressPastDays(date);
                prune();
                today = new JournalFile(dir.resolve(date + ".log"));
                todayDate = date;
            }
            today.append(GSON.toJson(e));
        } catch (IOException ex) {
            System.err.println("[MineAgent] Journal write failed: " + ex.getMessage());
        }
    }

    // ── Reading ────────────────────────────────────────────────────

    public synchronized List<Event> recent(int n) {
        List<Event> all = new ArrayList<>(recent);
        return all.subList(Math.max(0, all.size() - n), all.size());
    }

    /** All of a day's events, oldest first (as far as it keeps days in full). */
    public synchronized List<Event> day(LocalDate date) {
        return read(date.toString());
    }

    public synchronized Day summary(LocalDate date) {
        return days.get(date.toString());
    }

    /** The newest events whose words hold every word asked for, over the days it keeps. */
    public synchronized List<Event> search(String words, String kind, int limit) {
        String[] want = words == null ? new String[0] : words.toLowerCase(Locale.ROOT).split("\\s+");
        List<Event> out = new ArrayList<>();
        LocalDate d = LocalDate.now(clock);
        for (int back = 0; back < keepDays && out.size() < limit; back++) {
            List<Event> list = read(d.minusDays(back).toString());
            for (int i = list.size() - 1; i >= 0 && out.size() < limit; i--) {
                Event e = list.get(i);
                if (kind != null && !kind.equals(e.kind())) continue;
                String t = e.text().toLowerCase(Locale.ROOT);
                boolean all = true;
                for (String w : want) if (!w.isBlank() && !t.contains(w)) all = false;
                if (all) out.add(e);
            }
            if (dir == null) break;
        }
        return out;
    }

    /** "Today: 4 orders, 2 fights; found a village; named the mine." From the summary, never the model. */
    public synchronized String describe(LocalDate date, String label) {
        Day d = days.get(date.toString());
        if (d == null || d.counts().isEmpty()) return label + ": nothing in my journal.";
        StringBuilder sb = new StringBuilder(label).append(": ");
        List<String> parts = new ArrayList<>();
        for (var c : d.counts().entrySet()) {
            String k = c.getKey();
            int n = c.getValue();
            String noun = switch (k) {
                case "order" -> n == 1 ? "order" : "orders";
                case "said" -> n == 1 ? "thing said" : "things said";
                case "fight" -> n == 1 ? "fight" : "fights";
                case "death" -> n == 1 ? "death" : "deaths";
                case "find" -> n == 1 ? "find" : "finds";
                case "goal" -> n == 1 ? "goal" : "goals";
                case "place" -> n == 1 ? "place named" : "places named";
                case "stored" -> n == 1 ? "thing stored" : "things stored";
                default -> k;
            };
            parts.add(n + " " + noun);
        }
        sb.append(String.join(", ", parts));
        if (!d.highlights().isEmpty()) {
            sb.append(". ");
            List<String> h = d.highlights();
            sb.append(String.join("; ", h.subList(Math.max(0, h.size() - 6), h.size())));
        }
        return sb.append('.').toString().replace("..", ".");
    }

    private List<Event> read(String date) {
        List<Event> out = new ArrayList<>();
        if (dir == null) {
            for (Event e : recent) if (dateOf(e.at()).equals(date)) out.add(e);
            return out;
        }
        try {
            List<String> lines;
            Path log = dir.resolve(date + ".log"), gz = dir.resolve(date + ".log.gz");
            if (Files.exists(log)) {
                lines = JournalFile.read(log);
            } else if (Files.exists(gz)) {
                lines = StoreFile.read(gz).contents().map(c -> List.of(c.body().split("\n"))).orElse(List.of());
            } else {
                return out;
            }
            for (String line : lines) {
                try {
                    Event e = GSON.fromJson(line, Event.class);
                    if (e != null && e.kind() != null) out.add(e);
                } catch (RuntimeException bad) {
                    // One bad line costs that line.
                }
            }
        } catch (IOException e) {
            System.err.println("[MineAgent] Journal read failed: " + e.getMessage());
        }
        return out;
    }

    private String dateOf(long millis) {
        return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toString();
    }

    /** A day that is over is kept compressed and checksummed. */
    private void compressPastDays(String todayIs) {
        if (dir == null || !Files.isDirectory(dir)) return;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.filter(f -> f.getFileName().toString().endsWith(".log")).toList()) {
                String date = p.getFileName().toString().replace(".log", "");
                if (date.compareTo(todayIs) >= 0) continue;
                List<String> lines = JournalFile.read(p);
                StoreFile.write(dir.resolve(date + ".log.gz"), "event-day", 1, String.join("\n", lines));
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            System.err.println("[MineAgent] Journal compress failed: " + e.getMessage());
        }
    }

    /** Days past what it keeps in full go; their summaries stay. */
    private void prune() {
        if (dir == null || !Files.isDirectory(dir)) return;
        String oldest = LocalDate.now(clock).minusDays(keepDays).toString();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.toList()) {
                String n = p.getFileName().toString();
                if (!n.endsWith(".log.gz") && !n.endsWith(".log.gz.bak")) continue;
                if (n.substring(0, Math.min(10, n.length())).compareTo(oldest) < 0) Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            System.err.println("[MineAgent] Journal prune failed: " + e.getMessage());
        }
    }

    // ── The store's view ───────────────────────────────────────────

    @Override
    public void replay(String op, String json) { }

    @Override
    public synchronized Map<String, MemoryStore.Segment> takeDirty() {
        try {
            if (today != null) today.sync();
        } catch (IOException ignored) {
            // Synced at the next write.
        }
        if (!summariesDirty) return Map.of();
        summariesDirty = false;
        return Map.of("events/summaries.gz", new MemoryStore.Segment("event-days", 1, GSON.toJson(new ArrayList<>(days.values()))));
    }

    @Override
    public synchronized long count() {
        return total;
    }

    public synchronized void close() {
        try {
            if (today != null) today.close();
        } catch (IOException ignored) {
            // Closed by the system.
        }
        today = null;
        todayDate = null;
    }

}
