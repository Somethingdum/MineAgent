package com.mineagent.engine.memory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.mineagent.engine.memory.store.MemoryStore;
import com.mineagent.engine.memory.store.RegionKey;
import com.mineagent.engine.memory.store.RegionTable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Things it has seen (SMH plan 70, MA5.7): ore, animals, hostile mobs, villagers, notable blocks (spawners, bells,
 * portals), structures and dangers, where and when, up to 50,000 by default. Kept by region in the memory store and
 * loaded only when needed; a small directory of what each region holds stays in memory, so a lookup opens only the
 * regions that could answer it, nearest first. Sightings close together merge (ore by 4 blocks, mobs by 8). When it is
 * full it forgets by worth, not by count: what is rare, recent and used stays; mobs (they move) go first; structures
 * and dangers are kept longest. A sighting proved gone (the ore mined out) is struck off. Pure.
 */
public final class Sightings extends RegionTable<Sightings.Region> {
    public enum Kind { ORE, ANIMAL, MOB, VILLAGER, BLOCK, STRUCTURE, DANGER }

    public record Sighting(Kind kind, String what, String dim, int x, int y, int z, int count, long first, long last,
                           int uses) {}

    public static final class Region {
        final LinkedHashMap<String, Sighting> byKey = new LinkedHashMap<>();
    }

    public static final int DEFAULT_MAX = 50_000;
    static final int REGION_MAX = 6_000;
    /** A sighting's worth halves every seven game days (less for mobs, more for structures and dangers). */
    static final long HALF_LIFE = 168_000L;
    private static final Gson GSON = new Gson();

    private final int max;
    /** Region id to "KIND:what" to count. */
    private final Map<String, Map<String, Integer>> directory = new HashMap<>();
    private boolean directoryDirty;
    private long total;

    public Sightings(MemoryStore store, int max) {
        super(store, "sightings", 24);
        this.max = Math.max(100, max);
        if (store != null) {
            store.register(this);
            store.read("sightings/index.gz", "sightings-index").ifPresent(this::loadDirectory);
        }
    }

    // ── Names ──────────────────────────────────────────────────────

    /** A block or entity id as kept: no namespace; ore as its metal ("deepslate_iron_ore" is iron). */
    public static String base(String id) {
        if (id == null) return "";
        String s = id.toLowerCase(Locale.ROOT).strip();
        int colon = s.indexOf(':');
        if (colon >= 0) s = s.substring(colon + 1);
        if (s.startsWith("deepslate_")) s = s.substring("deepslate_".length());
        if (s.equals("nether_quartz_ore")) return "quartz";
        if (s.endsWith("_ore")) s = s.substring(0, s.length() - 4);
        return s;
    }

    /** What a person's words ask for: "diamonds", "iron ore", "the cows" become diamond, iron, cow. */
    public static String fromWords(String words) {
        if (words == null) return "";
        String w = words.toLowerCase(Locale.ROOT).strip().replaceFirst("^(?:the|some|any|a|an|my|our) ", "");
        w = w.replaceAll("\\s+ores?$", "").replace(' ', '_');
        if (w.endsWith("ies")) w = w.substring(0, w.length() - 3) + "y";
        else if (w.endsWith("ches") || w.endsWith("shes")) w = w.substring(0, w.length() - 2);
        else if (w.endsWith("s") && !w.endsWith("ss") && !w.equals("lapis") && !w.endsWith("_debris")) w = w.substring(0, w.length() - 1);
        return base(w);
    }

    public static double importance(Kind kind, String what) {
        return switch (kind) {
            case ORE -> switch (what) {
                case "diamond", "ancient_debris" -> 1.0;
                case "emerald" -> 0.9;
                case "iron" -> 0.7;
                case "gold", "lapis" -> 0.6;
                case "redstone" -> 0.5;
                case "coal", "quartz", "nether_gold" -> 0.4;
                default -> 0.35;
            };
            case ANIMAL -> 0.3;
            case MOB -> 0.2;
            case VILLAGER -> 0.6;
            case BLOCK -> switch (what) {
                case "end_portal_frame" -> 1.0;
                case "spawner", "nether_portal", "trial_spawner", "vault" -> 0.9;
                case "bell", "enchanting_table" -> 0.8;
                case "brewing_stand" -> 0.7;
                default -> 0.5;
            };
            case STRUCTURE -> 0.9;
            case DANGER -> 0.7;
        };
    }

    static long halfLife(Kind kind) {
        return switch (kind) {
            case MOB, ANIMAL -> HALF_LIFE / 4;
            case STRUCTURE, DANGER -> HALF_LIFE * 4;
            case BLOCK -> HALF_LIFE * 2;
            default -> HALF_LIFE;
        };
    }

    /** What a sighting is worth keeping now: how rare, how used, how recent. */
    static double worth(Sighting s, long now) {
        double age = Math.max(0, now - s.last());
        return importance(s.kind(), s.what()) * (1.0 + Math.log1p(s.uses())) * Math.pow(0.5, age / halfLife(s.kind()));
    }

    private static int bucketBits(Kind kind) {
        return switch (kind) {
            case ORE -> 2;
            case ANIMAL, MOB, VILLAGER, DANGER -> 3;
            case STRUCTURE -> 5;
            case BLOCK -> 0;
        };
    }

    static String key(Kind kind, String what, int x, int y, int z) {
        int b = bucketBits(kind);
        return kind + ":" + what + "@" + (x >> b) + "," + (y >> b) + "," + (z >> b);
    }

    // ── Seeing ─────────────────────────────────────────────────────

    /** It saw {@code count} of {@code what} at x, y, z (several close by merge into one); whether it is new to it. */
    public boolean see(Kind kind, String what, String dim, int x, int y, int z, int count, long tick) {
        String w = base(what);
        if (w.isEmpty() || kind == null || dim == null) return false;
        RegionKey rk = RegionKey.ofBlock(dim, x, z);
        String k = key(kind, w, x, y, z);
        Sighting merged;
        boolean fresh;
        synchronized (this) {
            Region r = region(rk);
            Sighting old = r.byKey.get(k);
            fresh = old == null;
            // Things that move are where they were seen last; the rest stay where first seen.
            boolean moves = kind == Kind.ANIMAL || kind == Kind.MOB || kind == Kind.VILLAGER;
            merged = fresh ? new Sighting(kind, w, dim, x, y, z, Math.max(1, count), tick, tick, 0)
                    : moves ? new Sighting(kind, w, dim, x, y, z, Math.max(old.count(), count), old.first(), Math.max(old.last(), tick), old.uses())
                    : new Sighting(kind, w, dim, old.x(), old.y(), old.z(), Math.max(old.count(), count), old.first(), Math.max(old.last(), tick), old.uses());
            put(rk, r, k, merged, fresh);
            if (fresh) {
                if (r.byKey.size() > REGION_MAX) evictIn(rk, r, r.byKey.size() - REGION_MAX, tick);
                if (total > max) evictGlobal(tick);
            }
        }
        // Only what is new goes into the journal: a later time seen is written with the region a minute later at most.
        if (fresh && store != null) store.log(name(), "put", GSON.toJson(merged));
        return fresh;
    }

    /** It saw that it is gone (mined out, killed, not there): struck off. */
    public void gone(Kind kind, String what, String dim, int x, int y, int z) {
        String w = base(what);
        RegionKey rk = RegionKey.ofBlock(dim, x, z);
        String k = key(kind, w, x, y, z);
        boolean removed;
        synchronized (this) {
            removed = remove(rk, region(rk), k);
        }
        if (removed && store != null) {
            JsonObject o = new JsonObject();
            o.addProperty("kind", kind.name());
            o.addProperty("what", w);
            o.addProperty("dim", dim);
            o.addProperty("x", x);
            o.addProperty("y", y);
            o.addProperty("z", z);
            store.log(name(), "gone", o.toString());
        }
    }

    private void put(RegionKey rk, Region r, String k, Sighting s, boolean fresh) {
        r.byKey.put(k, s);
        touched(rk);
        if (fresh) {
            total++;
            directory.computeIfAbsent(rk.id(), x -> new HashMap<>()).merge(s.kind() + ":" + s.what(), 1, Integer::sum);
            directoryDirty = true;
        }
    }

    private boolean remove(RegionKey rk, Region r, String k) {
        Sighting s = r.byKey.remove(k);
        if (s == null) return false;
        touched(rk);
        total--;
        var counts = directory.get(rk.id());
        if (counts != null) {
            counts.computeIfPresent(s.kind() + ":" + s.what(), (a, n) -> n <= 1 ? null : n - 1);
            if (counts.isEmpty()) directory.remove(rk.id());
        }
        directoryDirty = true;
        return true;
    }

    private void evictIn(RegionKey rk, Region r, int n, long now) {
        List<Map.Entry<String, Sighting>> all = new ArrayList<>(r.byKey.entrySet());
        all.sort(Comparator.comparingDouble(e -> worth(e.getValue(), now)));
        for (int i = 0; i < n && i < all.size(); i++) remove(rk, r, all.get(i).getKey());
    }

    /** Over the cap: the least worth goes, from the fullest region first. */
    /*
     * Full: down to 99% (room for more, so it is not trimming on every new sighting), from the fullest regions, a
     * quarter of a region at a time, least worth first. This took "over + 1%" from one region, capped at its size, so
     * with 50,000 over 400 regions one new sighting emptied a whole region whatever its things were worth.
     */
    /** Region ids parsed once: a lookup used to parse all of them every time. */
    private final Map<String, RegionKey> parsedKeys = new HashMap<>();

    private RegionKey parsedKey(String id) {
        return parsedKeys.computeIfAbsent(id, RegionKey::parse);
    }

    private void evictGlobal(long now) {
        long target = max - max / 100;
        int guard = 0;
        while (total > target && guard++ < 1_000) {
            String fullest = null;
            int most = 0;
            for (var e : directory.entrySet()) {
                int n = e.getValue().values().stream().mapToInt(Integer::intValue).sum();
                if (n > most) {
                    most = n;
                    fullest = e.getKey();
                }
            }
            if (fullest == null) return;
            RegionKey rk = parsedKey(fullest);
            Region r = region(rk);
            evictIn(rk, r, (int) Math.max(1, Math.min(total - target, r.byKey.size() / 4)), now);
        }
    }

    // ── Looking up ─────────────────────────────────────────────────

    /** A sighting with how far it is from where the question was asked. */
    public record Found(Sighting sighting, double distance) {}

    /**
     * The nearest sightings of what was asked (a block or entity, "iron", or a kind, "ore"), in this dimension, within
     * {@code maxDistance}, at most {@code limit}. Each one found counts as used.
     */
    public List<Found> nearest(String query, String dim, int x, int y, int z, int limit, double maxDistance) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).strip();
        if (q.isEmpty() || limit <= 0) return List.of();
        Kind asKind = null;
        for (Kind k : Kind.values()) if (k.name().equalsIgnoreCase(q)) asKind = k;
        final Kind kindQuery = asKind;
        List<Found> best = new ArrayList<>();
        // The directory words a region holds this under: one per kind, looked up directly.
        String[] wanted = new String[Kind.values().length];
        for (Kind k : Kind.values()) wanted[k.ordinal()] = k.name() + ":" + q;
        String kindPrefix = kindQuery == null ? null : kindQuery.name() + ":";
        synchronized (this) {
            List<RegionKey> candidates = new ArrayList<>();
            for (var e : directory.entrySet()) {
                RegionKey rk = parsedKey(e.getKey());
                if (!rk.dim().equals(dim) || rk.distanceFrom(x, z) > maxDistance) continue;
                var words = e.getValue();
                boolean has = false;
                if (kindPrefix != null) {
                    for (String kw : words.keySet()) if (kw.startsWith(kindPrefix)) { has = true; break; }
                } else {
                    for (String kw : wanted) if (words.containsKey(kw)) { has = true; break; }
                }
                if (has) candidates.add(rk);
            }
            candidates.sort(Comparator.comparingDouble(rk -> rk.distanceFrom(x, z)));
            for (RegionKey rk : candidates) {
                double near = rk.distanceFrom(x, z);
                if (near > maxDistance) break;
                if (best.size() >= limit && near > best.get(best.size() - 1).distance()) break;
                for (Sighting s : region(rk).byKey.values()) {
                    if (kindQuery != null ? s.kind() != kindQuery : !s.what().equals(q)) continue;
                    double dx = s.x() - x, dy = s.y() - y, dz = s.z() - z;
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d > maxDistance) continue;
                    best.add(new Found(s, d));
                }
                best.sort(Comparator.comparingDouble(Found::distance));
                if (best.size() > limit) best.subList(limit, best.size()).clear();
            }
            for (Found f : best) {
                Sighting s = f.sighting();
                RegionKey rk = RegionKey.ofBlock(s.dim(), s.x(), s.z());
                Region r = region(rk);
                String k = key(s.kind(), s.what(), s.x(), s.y(), s.z());
                if (r.byKey.containsKey(k)) {
                    r.byKey.put(k, new Sighting(s.kind(), s.what(), s.dim(), s.x(), s.y(), s.z(), s.count(), s.first(), s.last(), s.uses() + 1));
                    touched(rk);
                }
            }
        }
        return best;
    }

    /** Whether anything of this name has been seen anywhere (without loading a region). */
    public synchronized boolean known(String what) {
        String w = base(what);
        for (var counts : directory.values()) for (String kw : counts.keySet()) if (kw.endsWith(":" + w)) return true;
        return false;
    }

    @Override
    public synchronized long count() {
        return total;
    }

    // ── Saving ─────────────────────────────────────────────────────

    @Override
    protected Region empty() {
        return new Region();
    }

    @Override
    protected String encode(Region region) {
        return GSON.toJson(new ArrayList<>(region.byKey.values()));
    }

    @Override
    protected Region decode(String body) {
        Region r = new Region();
        List<Sighting> list = GSON.fromJson(body, new TypeToken<List<Sighting>>() {}.getType());
        if (list != null) {
            for (Sighting s : list) {
                if (s != null && s.kind() != null && s.what() != null && s.dim() != null) {
                    r.byKey.put(key(s.kind(), s.what(), s.x(), s.y(), s.z()), s);
                }
            }
        }
        return r;
    }

    @Override
    protected synchronized Map<String, MemoryStore.Segment> dirtyExtras() {
        if (!directoryDirty) return Map.of();
        directoryDirty = false;
        JsonObject o = new JsonObject();
        o.addProperty("total", total);
        o.add("regions", GSON.toJsonTree(directory));
        return Map.of("sightings/index.gz", new MemoryStore.Segment("sightings-index", VERSION, o.toString()));
    }

    private synchronized void loadDirectory(String body) {
        JsonObject o = GSON.fromJson(body, JsonObject.class);
        if (o == null) return;
        Map<String, Map<String, Integer>> d = GSON.fromJson(o.get("regions"),
                new TypeToken<Map<String, Map<String, Integer>>>() {}.getType());
        directory.clear();
        if (d != null) directory.putAll(d);
        total = o.has("total") ? o.get("total").getAsLong() : 0L;
    }

    @Override
    public void replay(String op, String json) {
        if ("put".equals(op)) {
            Sighting s = GSON.fromJson(json, Sighting.class);
            if (s == null || s.kind() == null || s.what() == null || s.dim() == null) return;
            RegionKey rk = RegionKey.ofBlock(s.dim(), s.x(), s.z());
            synchronized (this) {
                Region r = region(rk);
                String k = key(s.kind(), s.what(), s.x(), s.y(), s.z());
                put(rk, r, k, s, !r.byKey.containsKey(k));
            }
        } else if ("gone".equals(op)) {
            JsonObject o = GSON.fromJson(json, JsonObject.class);
            Kind kind = Kind.valueOf(o.get("kind").getAsString());
            String dim = o.get("dim").getAsString();
            int x = o.get("x").getAsInt(), y = o.get("y").getAsInt(), z = o.get("z").getAsInt();
            RegionKey rk = RegionKey.ofBlock(dim, x, z);
            synchronized (this) {
                remove(rk, region(rk), key(kind, o.get("what").getAsString(), x, y, z));
            }
        }
    }
}
