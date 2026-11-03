package com.mineagent.engine.planning.mining;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The ore features of the running game (SMH plan 70, MA7.8: "from the game's ore settings"): every placed feature
 * that puts ore, its height range, its tries a chunk and its vein size, read through the game's own codecs, so a
 * datapack's or a mod's ores count too. Read once per server. {@link OrePlan} picks the height.
 */
public final class OreHeights {
    private static volatile Map<String, List<OrePlan.Feature>> cached;
    private static volatile Object cachedFor;

    private OreHeights() {}

    /** The height to branch-mine at for this ore block in this world (Y 16 if the game says nothing). */
    public static int bestY(ServerLevel level, String oreBlock) {
        var features = features(level).getOrDefault(oreBlock, List.of());
        return OrePlan.bestY(features, level.getMinBuildHeight(), Math.min(56, level.getSeaLevel() - 7));
    }

    public static Map<String, List<OrePlan.Feature>> features(ServerLevel level) {
        Object server = level.getServer();
        if (cached == null || cachedFor != server) {
            Map<String, List<OrePlan.Feature>> read;
            try {
                read = read(level);
            } catch (RuntimeException e) {
                read = Map.of();
            }
            cached = read;
            cachedFor = server;
        }
        return cached;
    }

    private static Map<String, List<OrePlan.Feature>> read(ServerLevel level) {
        Map<String, List<OrePlan.Feature>> out = new HashMap<>();
        var access = level.registryAccess();
        var ops = RegistryOps.create(JsonOps.INSTANCE, access);
        for (var entry : access.registryOrThrow(Registries.PLACED_FEATURE).entrySet()) {
            try {
                var placed = entry.getValue();
                if (!(placed.feature().value().config() instanceof OreConfiguration ore)) continue;
                double count = 1;
                String shape = null;
                int lo = 0, hi = 0;
                for (PlacementModifier m : placed.placement()) {
                    JsonElement j = PlacementModifier.CODEC.encodeStart(ops, m).result().orElse(null);
                    if (j == null || !j.isJsonObject()) continue;
                    JsonObject o = j.getAsJsonObject();
                    String type = o.has("type") ? o.get("type").getAsString() : "";
                    if (type.endsWith(":count")) {
                        count = number(o.get("count"));
                    } else if (type.endsWith(":rarity_filter") && o.has("chance")) {
                        count /= Math.max(1, o.get("chance").getAsInt());
                    } else if (type.endsWith(":height_range") && o.has("height")) {
                        JsonElement h = o.get("height");
                        if (!h.isJsonObject()) continue;
                        JsonObject ho = h.getAsJsonObject();
                        if (ho.has("min_inclusive") && ho.has("max_inclusive")) {
                            shape = ho.has("type") && ho.get("type").getAsString().endsWith("trapezoid") ? "trapezoid" : "uniform";
                            lo = anchor(ho.get("min_inclusive"), level);
                            hi = anchor(ho.get("max_inclusive"), level);
                        } else {
                            shape = "uniform"; // one height
                            lo = hi = anchor(ho, level);
                        }
                    }
                }
                if (shape == null) continue;
                for (var target : ore.targetStates) {
                    String block = BuiltInRegistries.BLOCK.getKey(target.state.getBlock()).toString();
                    out.computeIfAbsent(block, k -> new ArrayList<>()).add(new OrePlan.Feature(shape, lo, hi, count, ore.size));
                }
            } catch (RuntimeException odd) {
                // A feature a mod builds oddly: left out.
            }
        }
        return out;
    }

    /** An int provider as the codec writes it: a number, or a range ({@code min_inclusive} to {@code max_inclusive}). */
    private static double number(JsonElement e) {
        if (e == null) return 1;
        if (e.isJsonPrimitive()) return e.getAsDouble();
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("min_inclusive") && o.has("max_inclusive")) {
                return (number(o.get("min_inclusive")) + number(o.get("max_inclusive"))) / 2.0;
            }
            if (o.has("value")) return number(o.get("value"));
        }
        return 1;
    }

    /** A vertical anchor: {"absolute": y}, {"above_bottom": n}, {"below_top": n}. */
    private static int anchor(JsonElement e, ServerLevel level) {
        if (e == null || !e.isJsonObject()) return 0;
        JsonObject o = e.getAsJsonObject();
        if (o.has("absolute")) return o.get("absolute").getAsInt();
        if (o.has("above_bottom")) return level.getMinBuildHeight() + o.get("above_bottom").getAsInt();
        if (o.has("below_top")) return level.getMaxBuildHeight() - 1 - o.get("below_top").getAsInt();
        return 0;
    }
}
