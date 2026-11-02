package com.mineagent.engine.skill;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashSet;
import java.util.Set;

/** Typed, literal bindings shared by action parameters and their verification conditions. */
public final class SkillBindings {
    private static final int MAX_DEPTH = 16;
    private SkillBindings() {}

    public static JsonObject reference(String name) {
        JsonObject reference = new JsonObject();
        reference.addProperty("$binding", name);
        return reference;
    }

    public static JsonObject defaults(JsonArray steps) {
        JsonObject result = new JsonObject();
        if (!steps.isEmpty() && steps.get(0).isJsonObject()) {
            JsonElement declared = steps.get(0).getAsJsonObject().get("bindings");
            if (declared != null) {
                if (!declared.isJsonObject()) throw new IllegalArgumentException("bindings must be an object");
                if (declared.getAsJsonObject().size() > 64) throw new IllegalArgumentException("Too many skill bindings");
                declared.getAsJsonObject().asMap().forEach((name, value) -> {
                    if (!name.matches("[a-zA-Z][a-zA-Z0-9_]{0,63}")) {
                        throw new IllegalArgumentException("Invalid skill binding name: " + name);
                    }
                    result.add(name, value.deepCopy());
                });
            }
        }
        return result;
    }

    public static JsonElement resolve(JsonElement source, JsonObject bindings) {
        return resolve(source, bindings, new HashSet<>(), 0);
    }

    public static void applyOverrides(JsonObject bindings, JsonObject overrides) {
        for (var entry : overrides.entrySet()) {
            String name = entry.getKey();
            JsonElement incoming = entry.getValue();
            if (!name.matches("[a-zA-Z][a-zA-Z0-9_]{0,63}") || incoming.isJsonNull()
                    || !bindings.has(name)) {
                throw new IllegalArgumentException("Invalid skill binding override: " + name);
            }
            JsonElement previous = bindings.get(name);
            if (previous != null && previous.isJsonPrimitive()) {
                if (!incoming.isJsonPrimitive()
                        || previous.getAsJsonPrimitive().isNumber() != incoming.getAsJsonPrimitive().isNumber()
                        || previous.getAsJsonPrimitive().isString() != incoming.getAsJsonPrimitive().isString()
                        || previous.getAsJsonPrimitive().isBoolean() != incoming.getAsJsonPrimitive().isBoolean()) {
                    throw new IllegalArgumentException("Skill binding type changed: " + name);
                }
                if (incoming.getAsJsonPrimitive().isNumber()
                        && !Double.isFinite(incoming.getAsDouble())) {
                    throw new IllegalArgumentException("Skill binding is not finite: " + name);
                }
            }
            bindings.add(name, incoming.deepCopy());
        }
    }

    /** A skill's steps with their coordinates made bindings, and whether any coordinate is still literal. */
    public record Lifted(JsonArray steps, boolean worldBound) {}

    private static final java.util.regex.Pattern COORDINATE =
            java.util.regex.Pattern.compile("-?\\d+,\\s*-?\\d+,\\s*-?\\d+");

    /**
     * SMH plan 69, MA2.0: a skill made portable for every world. Every literal {@code x}, {@code y} and
     * {@code z} in the steps (arguments and verification conditions) and every {@code pos}, {@code target_pos} or {@code position}
     * value becomes a {@code $binding} reference ({@code x1}, {@code y1}, {@code z1}, {@code pos1}, ...),
     * with the old value declared as its default in step 0's {@code bindings}, so the skill replays as
     * before here and takes new places elsewhere. A step kept only as raw text that names a coordinate
     * cannot be lifted: the skill is then world-bound and stays in its world. The input is not changed.
     */
    public static Lifted lift(JsonArray steps) {
        JsonArray out = steps.deepCopy();
        if (out.isEmpty() || !out.get(0).isJsonObject()) {
            return new Lifted(out, hasRawCoordinate(out));
        }
        JsonObject declared = defaults(out);
        int[] counter = {highestIndex(declared)};
        for (JsonElement step : out) {
            if (!step.isJsonObject()) continue;
            JsonObject object = step.getAsJsonObject();
            // Arguments and verification conditions alike (they share the bindings).
            for (String key : new java.util.ArrayList<>(object.keySet())) {
                if (key.equals("tool") || key.equals("bindings") || key.equals("args_raw")) continue;
                object.add(key, liftValue(object.get(key), declared, counter, 0));
            }
        }
        if (declared.size() > 64) return new Lifted(steps.deepCopy(), true);
        if (!declared.isEmpty()) out.get(0).getAsJsonObject().add("bindings", declared);
        return new Lifted(out, hasRawCoordinate(out));
    }

    private static JsonElement liftValue(JsonElement value, JsonObject declared, int[] counter, int depth) {
        if (depth > MAX_DEPTH) return value;
        if (value.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement e : value.getAsJsonArray()) result.add(liftValue(e, declared, counter, depth + 1));
            return result;
        }
        if (!value.isJsonObject()) return value;
        JsonObject object = value.getAsJsonObject();
        if (object.has("$binding")) return object;
        boolean triple = isNumber(object.get("x")) && isNumber(object.get("y")) && isNumber(object.get("z"));
        int n = triple ? ++counter[0] : 0;
        JsonObject result = new JsonObject();
        for (var entry : object.entrySet()) {
            String key = entry.getKey();
            JsonElement v = entry.getValue();
            if (triple && (key.equals("x") || key.equals("y") || key.equals("z"))) {
                result.add(key, bind(declared, key + n, v));
            } else if ((key.equals("pos") || key.equals("target_pos") || key.equals("position"))
                    && (v.isJsonObject() || v.isJsonArray()) && !isReference(v)) {
                result.add(key, bind(declared, key.replace("_", "") + (++counter[0]), v));
            } else {
                result.add(key, liftValue(v, declared, counter, depth + 1));
            }
        }
        return result;
    }

    private static JsonObject bind(JsonObject declared, String name, JsonElement value) {
        declared.add(name, value.deepCopy());
        return reference(name);
    }

    private static boolean isNumber(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber();
    }

    private static boolean isReference(JsonElement e) {
        return e.isJsonObject() && e.getAsJsonObject().has("$binding");
    }

    private static int highestIndex(JsonObject declared) {
        int max = 0;
        for (String name : declared.keySet()) {
            String digits = name.replaceAll("^[a-zA-Z_]+", "");
            if (!digits.isEmpty() && digits.chars().allMatch(Character::isDigit) && digits.length() < 6) {
                max = Math.max(max, Integer.parseInt(digits));
            }
        }
        return max;
    }

    private static boolean hasRawCoordinate(JsonArray steps) {
        for (JsonElement step : steps) {
            if (step.isJsonObject() && step.getAsJsonObject().has("args_raw")
                    && COORDINATE.matcher(step.getAsJsonObject().get("args_raw").toString()).find()) {
                return true;
            }
        }
        return false;
    }

    private static JsonElement resolve(JsonElement source, JsonObject bindings,
                                        Set<String> resolving, int depth) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("Skill bindings are too deeply nested");
        if (source.isJsonObject()) {
            JsonObject object = source.getAsJsonObject();
            if (object.has("$binding")) {
                if (object.size() != 1 || !object.get("$binding").isJsonPrimitive()
                        || !object.getAsJsonPrimitive("$binding").isString()) {
                    throw new IllegalArgumentException("A binding reference must contain only a string $binding");
                }
                String name = object.get("$binding").getAsString();
                if (!name.matches("[a-zA-Z][a-zA-Z0-9_]{0,63}") || !bindings.has(name)
                        || bindings.get(name).isJsonNull()) {
                    throw new IllegalArgumentException("Missing or invalid skill binding: " + name);
                }
                if (!resolving.add(name)) throw new IllegalArgumentException("Cyclic skill binding: " + name);
                JsonElement resolved = resolve(bindings.get(name), bindings, resolving, depth + 1);
                resolving.remove(name);
                return resolved;
            }
            JsonObject result = new JsonObject();
            object.asMap().forEach((name, value) -> result.add(name,
                    resolve(value, bindings, resolving, depth + 1)));
            return result;
        }
        if (source.isJsonArray()) {
            JsonArray result = new JsonArray();
            source.getAsJsonArray().forEach(value -> result.add(resolve(value, bindings, resolving, depth + 1)));
            return result;
        }
        return source.deepCopy();
    }
}
