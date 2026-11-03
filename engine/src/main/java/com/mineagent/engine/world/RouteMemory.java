package com.mineagent.engine.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * Where things are and how to get there (SMH plan 70, MA5.6; the Andy Omega plan's places, routes and map_route):
 * places with a kind and a name, the trail of the trip under way, and routes between places walked before. Pure: the
 * caller feeds positions; nothing here reads the world.
 */
public final class RouteMemory {
    public static final int MAX_PLACES = 4096;
    public static final int MAX_ROUTES = 8192;
    /** A point in the open every this many blocks. */
    static final double OPEN_STEP = 8.0;
    /** Underground, a point at every turn or change of height, at least this far from the last. */
    static final double CAVE_STEP = 2.0;
    /** A trip's end this close to a place counts as reaching it. */
    public static final double AT_PLACE = 6.0;
    /** A simplified route keeps a point at least every this many blocks. */
    static final double ROUTE_STEP = 16.0;

    public record Point(String dim, int x, int y, int z) {
        public double distance(Point o) {
            double dx = x - o.x, dy = y - o.y, dz = z - o.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    /**
     * A place: {@code kind} home, base, mine, village, farm, portal, bed, danger, chest, or a name you gave
     * ({@code named}); {@code confidence} 0..1, how sure it is still there; {@code checked} the game tick it was last
     * seen there.
     */
    public record Place(String id, String kind, String name, Point at, double confidence, long checked) {}

    /** A way walked between two places, the trail simplified. */
    public record Route(String from, String to, List<Point> waypoints, double length, int uses, int successes, long last) {}

    public record State(List<Place> places, List<Route> routes, List<Point> trail, String trailFrom, int nextId) {}

    /** A trip it made: from one known place (or none) to another, the way it went, simplified (MA5.7). */
    public record Trip(String from, String to, long ended, List<Point> points) {}

    /** Places and routes, for the memory store. */
    public record PlacesState(List<Place> places, List<Route> routes, int nextId, List<PortalLink> portals) {}

    /**
     * A portal it went through (MA5.6): where it stepped in, and where it came out in the other dimension. Kept only
     * once it has been through: a link is never guessed from coordinates.
     */
    public record PortalLink(Point from, Point to, long tick) {}

    private final List<PortalLink> portals = new ArrayList<>();

    /** It went through a portal from {@code from} and came out at {@code to}: both ends remembered, linked both ways. */
    public synchronized void linkPortal(Point from, Point to, long tick) {
        if (from == null || to == null || from.dim().equals(to.dim())) return;
        portals.removeIf(l -> (l.from().dim().equals(from.dim()) && l.from().distance(from) <= 8.0)
                || (l.from().dim().equals(to.dim()) && l.from().distance(to) <= 8.0));
        portals.add(new PortalLink(from, to, tick));
        portals.add(new PortalLink(to, from, tick));
        while (portals.size() > 256) portals.remove(0);
        found("portal", from, tick);
        found("portal", to, tick);
        placesVersion++;
    }

    /** The nearest portal it has been through, in {@code near}'s dimension, that leads to {@code toDim}. */
    public synchronized Optional<PortalLink> portalTo(String toDim, Point near) {
        PortalLink best = null;
        double bestD = Double.MAX_VALUE;
        for (PortalLink l : portals) {
            if (!l.from().dim().equals(near.dim()) || !l.to().dim().equals(toDim)) continue;
            double d = l.from().distance(near);
            if (d < bestD) {
                bestD = d;
                best = l;
            }
        }
        return Optional.ofNullable(best);
    }

    public synchronized List<PortalLink> portals() {
        return List.copyOf(portals);
    }

    /** The trail under way. */
    public record TrailState(List<Point> trail, String trailFrom) {}

    /** The last trips kept in full; older ones live on in the routes they made. */
    public static final int MAX_TRIPS = 200;
    private final java.util.ArrayDeque<Trip> trips = new java.util.ArrayDeque<>();
    /** Bumped on each change, so the store writes only what changed. */
    private long placesVersion;
    private long trailVersion;
    private long tripsVersion;

    private final Map<String, Place> places = new LinkedHashMap<>();
    private final List<Route> routes = new ArrayList<>();
    private final List<Point> trail = new ArrayList<>();
    private String trailFrom;
    private int nextId = 1;
    /** The last position seen and the way it was going, to find the corners of a way underground. */
    private Point lastRaw;
    private int[] lastDir;

    // ── Places ─────────────────────────────────────────────────────

    /** Name a place (or move it): "this is home", "remember this as the mine". The same name replaces the old one. */
    public synchronized Place name(String kind, String name, Point at, long tick) {
        String k = clean(kind, "named");
        String n = clean(name, k);
        for (Place p : List.copyOf(places.values())) {
            if (p.name().equalsIgnoreCase(n) || ("home".equals(k) && "home".equals(p.kind()))) places.remove(p.id());
        }
        Place place = new Place("p" + nextId++, k, n, at, 1.0, tick);
        places.put(place.id(), place);
        trimPlaces();
        placesVersion++;
        return place;
    }

    /** Something it found (a village it walked into): kept once, the nearer copy within 64 blocks counts as the same. */
    public synchronized Place found(String kind, Point at, long tick) {
        for (Place p : places.values()) {
            if (p.kind().equals(kind) && p.at().dim().equals(at.dim()) && p.at().distance(at) <= 64.0) {
                Place seen = new Place(p.id(), p.kind(), p.name(), p.at(), 1.0, tick);
                places.put(p.id(), seen);
                placesVersion++;
                return seen;
            }
        }
        long same = places.values().stream().filter(p -> p.kind().equals(kind)).count();
        String name = same == 0 ? "the " + kind : "the " + kind + " " + (same + 1);
        Place place = new Place("p" + nextId++, kind, name, at, 1.0, tick);
        places.put(place.id(), place);
        trimPlaces();
        placesVersion++;
        return place;
    }

    /** A place by name ("home", "the mine", "village"), or the nearest of a kind. */
    public synchronized Optional<Place> find(String words, Point near) {
        if (words == null || words.isBlank()) return Optional.empty();
        String w = words.toLowerCase(Locale.ROOT).strip().replaceFirst("^(?:the|my|our) ", "");
        Place best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Place p : places.values()) {
            String name = p.name().toLowerCase(Locale.ROOT).replaceFirst("^(?:the|my|our) ", "");
            boolean match = name.equals(w) || p.kind().equals(w) || ("base".equals(w) && "home".equals(p.kind()))
                    || ("home".equals(w) && "base".equals(p.kind()));
            if (!match) continue;
            double d = near == null || !near.dim().equals(p.at().dim()) ? 1e9 : near.distance(p.at());
            if (best == null || d < bestDistance) {
                best = p;
                bestDistance = d;
            }
        }
        return Optional.ofNullable(best);
    }

    /** It looked and the place is not there (a chest gone, a village it can't find): less sure of it. */
    public synchronized void doubted(String id) {
        Place p = places.get(id);
        if (p == null) return;
        double c = p.confidence() * 0.6;
        if (c < 0.15 && !"home".equals(p.kind()) && !"named".equals(p.kind())) places.remove(id);
        else places.put(id, new Place(p.id(), p.kind(), p.name(), p.at(), Math.max(c, 0.15), p.checked()));
        placesVersion++;
    }

    public synchronized List<Place> places() {
        return List.copyOf(places.values());
    }

    private void trimPlaces() {
        while (places.size() > MAX_PLACES) {
            Place worst = places.values().stream()
                    .filter(p -> !"home".equals(p.kind()) && !"named".equals(p.kind()))
                    .min(Comparator.comparingDouble(Place::confidence).thenComparingLong(Place::checked))
                    .orElse(null);
            if (worst == null) break;
            places.remove(worst.id());
        }
    }

    // ── The trail ──────────────────────────────────────────────────

    /**
     * Where it is now. A point is kept every {@link #OPEN_STEP} blocks in the open, or underground ({@code
     * underground}) at every turn or change of height; a change of dimension starts a new trail.
     */
    public synchronized void at(Point now, boolean underground) {
        if (trail.isEmpty() || !trail.get(trail.size() - 1).dim().equals(now.dim())) {
            if (!trail.isEmpty()) trailFrom = null; // a new dimension: a new trail
            trail.clear();
            trail.add(now);
            lastRaw = now;
            lastDir = null;
            trailVersion++;
            return;
        }
        if (now.equals(lastRaw)) return;
        Point last = trail.get(trail.size() - 1);
        int[] dir = direction(lastRaw, now);
        if (last.distance(now) >= OPEN_STEP) {
            trail.add(now);
        } else if (underground) {
            // A corner: the way it went changed at the last position seen.
            if (lastDir != null && !java.util.Arrays.equals(dir, lastDir) && last.distance(lastRaw) >= CAVE_STEP) {
                trail.add(lastRaw);
                last = lastRaw;
            }
            if (now.y() != last.y() && last.distance(now) >= CAVE_STEP) trail.add(now);
        }
        lastDir = dir;
        lastRaw = now;
        if (trail.size() > 4000) trail.subList(0, trail.size() - 4000).clear();
        trailVersion++;
    }

    private static int[] direction(Point a, Point b) {
        return new int[] {Integer.signum(b.x() - a.x()), Integer.signum(b.y() - a.y()), Integer.signum(b.z() - a.z())};
    }

    /**
     * Where it is now, on the way: the trail is kept, and a trip from one known place that reaches another becomes a
     * route (returned). Reaching a place with no trip under way starts one from there.
     */
    public synchronized Optional<Route> walked(Point now, boolean underground, long tick) {
        at(now, underground);
        Place here = nearest(now, AT_PLACE);
        if (here == null) return Optional.empty();
        if (trailFrom == null) {
            startTrip(here.id(), now);
            return Optional.empty();
        }
        if (!here.id().equals(trailFrom) && trail.size() >= 2) return endTrip(now, tick);
        return Optional.empty();
    }

    /** The known place it stands at, if any. */
    public synchronized Optional<Place> placeAt(Point now) {
        return Optional.ofNullable(nearest(now, AT_PLACE));
    }

    /** The way back to where this trail began, nearest point first. */
    public synchronized List<Point> wayBack() {
        List<Point> back = new ArrayList<>(trail);
        java.util.Collections.reverse(back);
        return back;
    }

    /** The trip starts at this place (or nowhere known). */
    public synchronized void startTrip(String fromPlace, Point at) {
        closeTrip(null, -1L);
        trail.clear();
        trailVersion++;
        trailFrom = fromPlace;
        lastRaw = at;
        lastDir = null;
        if (at != null) trail.add(at);
    }

    /**
     * The trip ended at {@code at}. If it began at a known place and ended at another, the trail becomes (or adds to)
     * the route between them. The route kept, if any.
     */
    public synchronized Optional<Route> endTrip(Point at, long tick) {
        Optional<Route> kept = Optional.empty();
        Place end = nearest(at, AT_PLACE);
        if (trailFrom != null && end != null && !end.id().equals(trailFrom) && trail.size() >= 2) {
            List<Point> way = simplify(new ArrayList<>(trail));
            double length = 0;
            for (int i = 1; i < way.size(); i++) length += way.get(i - 1).distance(way.get(i));
            kept = Optional.of(keepRoute(trailFrom, end.id(), way, length, tick));
        }
        closeTrip(end == null ? null : end.id(), tick);
        trail.clear();
        trailVersion++;
        trailFrom = end == null ? null : end.id();
        trail.add(at);
        lastRaw = at;
        lastDir = null;
        return kept;
    }

    private Place nearest(Point at, double within) {
        Place best = null;
        double bestDistance = within;
        for (Place p : places.values()) {
            if (!p.at().dim().equals(at.dim())) continue;
            double d = p.at().distance(at);
            if (d <= bestDistance) {
                best = p;
                bestDistance = d;
            }
        }
        return best;
    }

    /** Keep a point at every real turn and at least every {@link #ROUTE_STEP} blocks; drop the rest. */
    public static List<Point> simplify(List<Point> points) {
        if (points.size() <= 2) return points;
        List<Point> out = new ArrayList<>();
        out.add(points.get(0));
        for (int i = 1; i < points.size() - 1; i++) {
            Point keptLast = out.get(out.size() - 1);
            Point here = points.get(i), next = points.get(i + 1);
            boolean far = keptLast.distance(next) > ROUTE_STEP;
            boolean height = here.y() != keptLast.y();
            int dx1 = Integer.signum(here.x() - keptLast.x()), dz1 = Integer.signum(here.z() - keptLast.z());
            int dx2 = Integer.signum(next.x() - here.x()), dz2 = Integer.signum(next.z() - here.z());
            if (far || height || dx1 != dx2 || dz1 != dz2) out.add(here);
        }
        out.add(points.get(points.size() - 1));
        return out;
    }

    // ── Routes ─────────────────────────────────────────────────────

    private Route keepRoute(String from, String to, List<Point> way, double length, long tick) {
        for (int i = 0; i < routes.size(); i++) {
            Route r = routes.get(i);
            if (r.from().equals(from) && r.to().equals(to)) {
                // The shorter way walked is kept; uses and successes add up.
                List<Point> best = length < r.length() ? way : r.waypoints();
                Route merged = new Route(from, to, best, Math.min(length, r.length()), r.uses() + 1, r.successes() + 1, tick);
                routes.set(i, merged);
                placesVersion++;
                return merged;
            }
        }
        Route route = new Route(from, to, way, length, 1, 1, tick);
        routes.add(route);
        placesVersion++;
        while (routes.size() > MAX_ROUTES) {
            routes.stream().min(Comparator.comparingLong(Route::last)).ifPresent(routes::remove);
        }
        return route;
    }

    /** A route it tried and could not walk (a lava flow, a new wall): it counts against the route. */
    public synchronized void routeFailed(String from, String to) {
        for (int i = 0; i < routes.size(); i++) {
            Route r = routes.get(i);
            if (r.from().equals(from) && r.to().equals(to)) {
                routes.set(i, new Route(from, to, r.waypoints(), r.length(), r.uses() + 1, r.successes(), r.last()));
                placesVersion++;
            }
        }
    }

    /**
     * The shortest known chain of routes from place {@code from} to place {@code to} (routes walk either way), as
     * waypoints; empty when none is known. A route that fails more than it works is avoided.
     */
    public synchronized List<Point> way(String from, String to) {
        if (from == null || to == null || from.equals(to)) return List.of();
        Map<String, Double> dist = new HashMap<>();
        Map<String, Route> via = new HashMap<>();
        Map<String, Boolean> reversed = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        PriorityQueue<Map.Entry<String, Double>> queue = new PriorityQueue<>(Map.Entry.comparingByValue());
        dist.put(from, 0.0);
        queue.add(Map.entry(from, 0.0));
        while (!queue.isEmpty()) {
            var e = queue.poll();
            String node = e.getKey();
            if (e.getValue() > dist.getOrDefault(node, Double.MAX_VALUE)) continue;
            if (node.equals(to)) break;
            for (Route r : routes) {
                boolean forward = r.from().equals(node);
                if (!forward && !r.to().equals(node)) continue;
                if (r.uses() >= 3 && r.successes() * 2 < r.uses()) continue; // fails more than it works
                String next = forward ? r.to() : r.from();
                double nd = e.getValue() + r.length();
                if (nd < dist.getOrDefault(next, Double.MAX_VALUE)) {
                    dist.put(next, nd);
                    via.put(next, r);
                    reversed.put(next, !forward);
                    prev.put(next, node);
                    queue.add(Map.entry(next, nd));
                }
            }
        }
        if (!dist.containsKey(to)) return List.of();
        List<List<Point>> legs = new ArrayList<>();
        for (String node = to; !node.equals(from); node = prev.get(node)) {
            List<Point> leg = new ArrayList<>(via.get(node).waypoints());
            if (reversed.get(node)) java.util.Collections.reverse(leg);
            legs.add(0, leg);
        }
        List<Point> out = new ArrayList<>();
        for (List<Point> leg : legs) {
            for (Point p : leg) if (out.isEmpty() || !out.get(out.size() - 1).equals(p)) out.add(p);
        }
        return out;
    }

    public synchronized List<Route> routes() {
        return List.copyOf(routes);
    }

    // ── Saving ─────────────────────────────────────────────────────

    public synchronized State exportState() {
        return new State(List.copyOf(places.values()), List.copyOf(routes), List.copyOf(trail), trailFrom, nextId);
    }

    public synchronized void importState(State s) {
        places.clear();
        routes.clear();
        trail.clear();
        if (s == null) return;
        if (s.places() != null) for (Place p : s.places()) if (p != null && p.id() != null && p.at() != null) places.put(p.id(), p);
        if (s.routes() != null) for (Route r : s.routes()) if (r != null && r.waypoints() != null) routes.add(r);
        if (s.trail() != null) for (Point p : s.trail()) if (p != null) trail.add(p);
        trailFrom = s.trailFrom();
        nextId = Math.max(s.nextId(), places.size() + 1);
        placesVersion++;
        trailVersion++;
    }

    // ── The memory store's view (MA5.7) ───────────────────────────

    private void closeTrip(String to, long tick) {
        if (trail.size() < 2) return;
        trips.addLast(new Trip(trailFrom, to, tick, simplify(new ArrayList<>(trail))));
        while (trips.size() > MAX_TRIPS) trips.removeFirst();
        tripsVersion++;
    }

    public synchronized List<Trip> trips() {
        return List.copyOf(trips);
    }

    /** Versions of places and routes, the trail, and the trips: each changes when that part does. */
    public synchronized long[] versions() {
        return new long[] {placesVersion, trailVersion, tripsVersion};
    }

    public synchronized PlacesState exportPlaces() {
        return new PlacesState(List.copyOf(places.values()), List.copyOf(routes), nextId, List.copyOf(portals));
    }

    public synchronized TrailState exportTrail() {
        return new TrailState(List.copyOf(trail), trailFrom);
    }

    public synchronized void importPlaces(PlacesState s) {
        if (s == null) return;
        places.clear();
        routes.clear();
        if (s.places() != null) for (Place p : s.places()) if (p != null && p.id() != null && p.at() != null) places.put(p.id(), p);
        if (s.routes() != null) for (Route r : s.routes()) if (r != null && r.waypoints() != null) routes.add(r);
        nextId = Math.max(s.nextId(), places.size() + 1);
        portals.clear();
        if (s.portals() != null) for (PortalLink l : s.portals()) if (l != null && l.from() != null && l.to() != null) portals.add(l);
    }

    public synchronized void importTrail(TrailState s) {
        if (s == null) return;
        trail.clear();
        if (s.trail() != null) for (Point p : s.trail()) if (p != null) trail.add(p);
        trailFrom = s.trailFrom();
        lastRaw = trail.isEmpty() ? null : trail.get(trail.size() - 1);
        lastDir = null;
    }

    public synchronized void importTrips(List<Trip> list) {
        trips.clear();
        if (list == null) return;
        for (Trip t : list) if (t != null && t.points() != null) trips.addLast(t);
        while (trips.size() > MAX_TRIPS) trips.removeFirst();
    }

    private static String clean(String s, String fallback) {
        if (s == null || s.isBlank()) return fallback;
        String t = s.strip().replaceAll("\\s+", " ");
        return t.length() > 48 ? t.substring(0, 48) : t;
    }
}
