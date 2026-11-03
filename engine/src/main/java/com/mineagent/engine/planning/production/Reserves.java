package com.mineagent.engine.planning.production;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What it keeps for itself and what is promised (SMH plan 70, MA7.4). The floors: food and torches it carries for its
 * own trips, never spent on an order nor put in a chest for one. The reservations: what a job has counted on from its
 * chests, so another job planned meanwhile doesn't count on the same iron. Pure.
 */
public final class Reserves {
    private Reserves() {}

    /**
     * What it carries less the floors: {@code foodFloor} items of food (taken from the foods it carries, in a fixed
     * order) and {@code torchFloor} torches.
     */
    public static Map<String, Integer> afterFloors(Map<String, Integer> carried, Set<String> foods, int foodFloor, int torchFloor) {
        Map<String, Integer> out = new HashMap<>(carried);
        int food = Math.max(0, foodFloor);
        List<String> mine = new ArrayList<>();
        for (String item : carried.keySet()) if (foods.contains(item)) mine.add(item);
        mine.sort(null);
        for (String item : mine) {
            if (food <= 0) break;
            int n = out.getOrDefault(item, 0);
            int keep = Math.min(n, food);
            out.put(item, n - keep);
            food -= keep;
        }
        String torch = "minecraft:torch";
        if (out.containsKey(torch)) out.put(torch, Math.max(0, out.get(torch) - Math.max(0, torchFloor)));
        out.values().removeIf(v -> v <= 0);
        return out;
    }

    /** Items promised to jobs, by job: so a job's plan doesn't count on what another's has. */
    public static final class Ledger {
        private final Map<String, Map<String, Integer>> byJob = new HashMap<>();

        public synchronized void reserve(String job, Map<String, Integer> items) {
            Map<String, Integer> m = byJob.computeIfAbsent(job, k -> new HashMap<>());
            items.forEach((item, n) -> { if (n > 0) m.merge(item, n, Integer::sum); });
        }

        public synchronized void release(String job) {
            byJob.remove(job);
        }

        /** What the other jobs have promised themselves, item by item. */
        public synchronized Map<String, Integer> byOthers(String job) {
            Map<String, Integer> out = new HashMap<>();
            byJob.forEach((j, m) -> { if (!j.equals(job)) m.forEach((item, n) -> out.merge(item, n, Integer::sum)); });
            return out;
        }

        /** {@code stored} less what other jobs have promised themselves. */
        public synchronized Map<String, Integer> free(Map<String, Integer> stored, String job) {
            Map<String, Integer> out = new HashMap<>(stored);
            byOthers(job).forEach((item, n) -> out.computeIfPresent(item, (k, v) -> Math.max(0, v - n)));
            out.values().removeIf(v -> v <= 0);
            return out;
        }
    }
}
