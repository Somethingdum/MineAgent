package com.mineagent.engine.settings;

/**
 * One of the behaviour switches or memory sizes changed (SMH plan 70), as {@code /mineagent setconfig} and the panel's
 * Behaviour tab send it. Pure: the config in, the config out.
 */
public final class SettingChange {
    private SettingChange() {}

    /**
     * {@code behaviour.<switch> true|false}, {@code behaviour.afkMinutes <n>} or {@code memory.<size> <n>}: the config
     * with that one setting changed, kept in range; null when the key or value is not one of them.
     */
    public static com.mineagent.api.config.MineAgentConfig withSetting(com.mineagent.api.config.MineAgentConfig config,
                                                                       String key, String value) {
        var gson = new com.google.gson.Gson();
        int dot = key.indexOf('.');
        if (dot < 0 || config == null) return null;
        String section = key.substring(0, dot), field = key.substring(dot + 1);
        String v = value.strip().toLowerCase(java.util.Locale.ROOT);
        try {
            if (section.equals("behaviour")) {
                var tree = gson.toJsonTree(config.behaviour()).getAsJsonObject();
                String name = matchKey(tree, field);
                if (name == null) return null;
                if (tree.get(name).getAsJsonPrimitive().isBoolean()) {
                    boolean on = switch (v) {
                        case "true", "on", "yes", "1" -> true;
                        case "false", "off", "no", "0" -> false;
                        default -> throw new NumberFormatException(v);
                    };
                    tree.addProperty(name, on);
                } else if (name.equals("afkStayWithin")) {
                    // A distance to keep within while they're AFK; "off" or 0 for none.
                    int n = java.util.Set.of("off", "none", "no", "nolimit").contains(v.replace(" ", "")) ? 0 : Integer.parseInt(v);
                    tree.addProperty(name, Math.max(0, Math.min(256, n)));
                } else if (name.equals("readyFood") || name.equals("readyTorches")) {
                    // What it takes on a trip: 0 to 64; "off" or "none" for none.
                    int n = java.util.Set.of("off", "none", "no").contains(v) ? 0 : Integer.parseInt(v);
                    tree.addProperty(name, Math.max(0, Math.min(64, n)));
                } else {
                    tree.addProperty(name, Math.max(1, Math.min(60, Integer.parseInt(v))));
                }
                var b = gson.fromJson(tree, com.mineagent.api.config.MineAgentConfig.BehaviourConfig.class);
                return new com.mineagent.api.config.MineAgentConfig(config.llm(), config.companion(), config.survival(),
                        config.pathfinding(), b, config.memory());
            }
            if (section.equals("memory")) {
                var tree = gson.toJsonTree(config.memory()).getAsJsonObject();
                String name = matchKey(tree, field);
                if (name == null) return null;
                int n = Integer.parseInt(v.replace(",", "").replace("_", ""));
                int clamped = switch (name) {
                    case "thingsSeen" -> Math.max(1_000, Math.min(500_000, n));
                    case "journalDays" -> Math.max(1, Math.min(3_650, n));
                    default -> Math.max(64, Math.min(16_384, n));
                };
                tree.addProperty(name, clamped);
                var m = gson.fromJson(tree, com.mineagent.api.config.MineAgentConfig.MemoryConfig.class);
                return new com.mineagent.api.config.MineAgentConfig(config.llm(), config.companion(), config.survival(),
                        config.pathfinding(), config.behaviour(), m);
            }
        } catch (RuntimeException bad) {
            return null;
        }
        return null;
    }

    private static String matchKey(com.google.gson.JsonObject tree, String field) {
        for (String k : tree.keySet()) if (k.equalsIgnoreCase(field)) return k;
        return null;
    }
}
