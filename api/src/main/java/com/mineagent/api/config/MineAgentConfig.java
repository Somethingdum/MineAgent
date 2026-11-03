package com.mineagent.api.config;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;

public record MineAgentConfig(
    LLMConfig llm,
    CompanionConfig companion,
    SurvivalConfig survival,
    PathfindingConfig pathfinding
) {
    public record LLMConfig(
        String provider,
        String apiKey,
        String model,
        String baseUrl,
        double temperature,
        int maxTokens
    ) {
        /**
         * SMH on this PC (SMH plan 69; the user, 2026-11-03: "Just drag and drop to the folder. And start SMH."):
         * smh answers the game on this PC at this address with no key, and picks the model for each call's role.
         */
        public static final LLMConfig DEFAULTS = new LLMConfig(
            "openai-compatible", "", "smh/auto", "http://127.0.0.1:47821/apps", 0.7, 4096
        );

        /** The upstream default this fork shipped before SMH: untouched, it reached no model (it has no key). */
        public static boolean untouchedOldDefault(String provider, String apiKey, String model, String baseUrl) {
            return "deepseek".equals(provider) && (apiKey == null || apiKey.isBlank())
                    && "deepseek-chat".equals(model) && (baseUrl == null || baseUrl.isBlank());
        }
    }

    public record CompanionConfig(
        String name,
        String gameMode,
        String skinName,
        boolean instantBreak,
        boolean creativeReach
    ) {
        public static final CompanionConfig DEFAULTS = new CompanionConfig(
            "MineAgent", "survival", "", false, false
        );
    }

    public record SurvivalConfig(
        int foodCritical,
        int foodLow,
        double healthFlee,
        int stuckTimeTicks,
        boolean autoEat,
        boolean fightBack,
        boolean pickupItems,
        boolean avoidCreeper
    ) {
        public static final SurvivalConfig DEFAULTS = new SurvivalConfig(
            6, 12, 6.0, 60, true, true, true, true
        );
    }

    public record PathfindingConfig(
        int maxSearchNodes,
        boolean allowDigThrough,
        boolean allowBridge,
        boolean allowParkour
    ) {
        public static final PathfindingConfig DEFAULTS = new PathfindingConfig(
            5000, true, true, true
        );
    }

    public static final MineAgentConfig DEFAULTS = new MineAgentConfig(
        LLMConfig.DEFAULTS, CompanionConfig.DEFAULTS,
        SurvivalConfig.DEFAULTS, PathfindingConfig.DEFAULTS
    );

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting().serializeNulls().create();

    public static MineAgentConfig load(Path configDir) {
        Path configFile = configDir.resolve("mineagent.json");
        if (Files.exists(configFile)) {
            try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (!parsed.isJsonObject()) {
                    throw new JsonParseException("configuration root must be an object");
                }
                // Overlay old/partial files onto current defaults. Gson maps a
                // missing record to null and missing primitive booleans to
                // false, which made ordinary version upgrades crash or disable
                // newly introduced safety features.
                JsonObject merged = GSON.toJsonTree(DEFAULTS).getAsJsonObject();
                mergeInto(merged, parsed.getAsJsonObject());
                return normalize(GSON.fromJson(merged, MineAgentConfig.class));
            } catch (Exception e) {
                System.err.println("[MineAgent] Failed to load config: " + e.getMessage());
                System.err.println("[MineAgent] Using defaults");
            }
        }
        // Save defaults
        saveDefaults(configFile);
        return DEFAULTS;
    }

    private static void mergeInto(JsonObject target, JsonObject source) {
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            JsonElement existing = target.get(entry.getKey());
            JsonElement incoming = entry.getValue();
            if (existing != null && existing.isJsonObject()
                    && incoming != null && incoming.isJsonObject()) {
                mergeInto(existing.getAsJsonObject(), incoming.getAsJsonObject());
            } else if (target.has(entry.getKey()) && incoming != null && !incoming.isJsonNull()) {
                target.add(entry.getKey(), incoming.deepCopy());
            }
        }
    }

    private static MineAgentConfig normalize(MineAgentConfig loaded) {
        if (loaded == null) return DEFAULTS;
        LLMConfig rawLlm = loaded.llm != null ? loaded.llm : LLMConfig.DEFAULTS;
        double temperature = Double.isFinite(rawLlm.temperature)
                && rawLlm.temperature >= 0.0 && rawLlm.temperature <= 2.0
                ? rawLlm.temperature : LLMConfig.DEFAULTS.temperature;
        int maxTokens = rawLlm.maxTokens > 0 && rawLlm.maxTokens <= 1_000_000
                ? rawLlm.maxTokens : LLMConfig.DEFAULTS.maxTokens;
        LLMConfig llm = new LLMConfig(
                nonBlank(rawLlm.provider, LLMConfig.DEFAULTS.provider),
                nonNull(rawLlm.apiKey),
                nonBlank(rawLlm.model, LLMConfig.DEFAULTS.model),
                nonNull(rawLlm.baseUrl), temperature, maxTokens);
        // A file written by an older jar with the old, keyless default: SMH on this PC instead (it never worked).
        if (LLMConfig.untouchedOldDefault(llm.provider, llm.apiKey, llm.model, llm.baseUrl)) {
            llm = new LLMConfig(LLMConfig.DEFAULTS.provider, "", LLMConfig.DEFAULTS.model,
                    LLMConfig.DEFAULTS.baseUrl, temperature, maxTokens);
        }

        CompanionConfig rawCompanion = loaded.companion != null
                ? loaded.companion : CompanionConfig.DEFAULTS;
        CompanionConfig companion = new CompanionConfig(
                nonBlank(rawCompanion.name, CompanionConfig.DEFAULTS.name),
                com.mineagent.api.entity.CompanionGameMode.orDefault(
                        rawCompanion.gameMode).wireName(),
                nonNull(rawCompanion.skinName), rawCompanion.instantBreak,
                rawCompanion.creativeReach);

        SurvivalConfig rawSurvival = loaded.survival != null
                ? loaded.survival : SurvivalConfig.DEFAULTS;
        int foodCritical = clamp(rawSurvival.foodCritical, 0, 20);
        int foodLow = clamp(rawSurvival.foodLow, foodCritical, 20);
        double healthFlee = Double.isFinite(rawSurvival.healthFlee)
                && rawSurvival.healthFlee >= 0.0
                ? rawSurvival.healthFlee : SurvivalConfig.DEFAULTS.healthFlee;
        SurvivalConfig survival = new SurvivalConfig(foodCritical, foodLow,
                healthFlee, Math.max(1, rawSurvival.stuckTimeTicks),
                rawSurvival.autoEat, rawSurvival.fightBack,
                rawSurvival.pickupItems, rawSurvival.avoidCreeper);

        PathfindingConfig rawPathfinding = loaded.pathfinding != null
                ? loaded.pathfinding : PathfindingConfig.DEFAULTS;
        PathfindingConfig pathfinding = new PathfindingConfig(
                clamp(rawPathfinding.maxSearchNodes, 128, 1_000_000),
                rawPathfinding.allowDigThrough, rawPathfinding.allowBridge,
                rawPathfinding.allowParkour);
        return new MineAgentConfig(llm, companion, survival, pathfinding);
    }

    private static String nonNull(String value) {
        return value != null ? value : "";
    }

    private static String nonBlank(String value, String fallback) {
        return value != null && !value.isBlank() ? value.trim() : fallback;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void saveDefaults(Path configFile) {
        try {
            Files.createDirectories(configFile.getParent());
            try (Writer writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(DEFAULTS, writer);
            }
            System.out.println("[MineAgent] Created default config at " + configFile);
        } catch (Exception e) {
            System.err.println("[MineAgent] Failed to save default config: " + e.getMessage());
        }
    }

    /**
     * Save this configuration to the {@code mineagent.json} file in the given
     * config directory. Used by the {@code /mineagent setconfig} command to
     * persist in-game config changes.
     *
     * @param configDir the platform config directory
     */
    public boolean save(Path configDir) {
        Path configFile = configDir.resolve("mineagent.json");
        try {
            Files.createDirectories(configFile.getParent());
            try (Writer writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
            System.out.println("[MineAgent] Config saved to " + configFile);
            return true;
        } catch (Exception e) {
            System.err.println("[MineAgent] Failed to save config: " + e.getMessage());
            return false;
        }
    }
}
