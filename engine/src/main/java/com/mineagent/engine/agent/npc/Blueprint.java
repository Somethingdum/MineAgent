// Ported from Mindcraft (github.com/mindcraft-bots/mindcraft, MIT, Copyright (c) 2024 Kolby Nottingham) at e1c1fc3: its
// construction blueprints (src/agent/npc/construction/*.json, copied into resources/mineagent/blueprints) read as its
// NPCContoller.init reads them: blocks[y][z][x], the first layer `offset` below the build's place, "" for any block, each
// layer padded with "" to a square of the blueprint's larger side.
package com.mineagent.engine.agent.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public record Blueprint(String name, int offset, String[][][] blocks) {

    private static final Map<String, Blueprint> LOADED = new ConcurrentHashMap<>();

    public int sizeX() {
        return blocks[0][0].length;
    }

    public int sizeZ() {
        return blocks[0].length;
    }

    public int sizeY() {
        return blocks.length;
    }

    /** The blueprint by its name ("dirt_shelter", "small_wood_house", ...), or null if there is none. */
    public static Blueprint named(String name) {
        return LOADED.computeIfAbsent(name, Blueprint::load);
    }

    private static Blueprint load(String name) {
        try (InputStream in = Blueprint.class.getResourceAsStream("/mineagent/blueprints/" + name + ".json")) {
            if (in == null) return null;
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray layers = o.getAsJsonArray("blocks");
            int sizeY = layers.size();
            int sizeZ = layers.get(0).getAsJsonArray().size();
            int sizeX = layers.get(0).getAsJsonArray().get(0).getAsJsonArray().size();
            // NPCContoller.init: each layer filled out to max_size by max_size with "".
            int max = Math.max(sizeX, sizeZ);
            String[][][] blocks = new String[sizeY][max][max];
            for (int y = 0; y < sizeY; y++) {
                JsonArray rows = layers.get(y).getAsJsonArray();
                for (int z = 0; z < max; z++) {
                    JsonArray row = z < rows.size() ? rows.get(z).getAsJsonArray() : new JsonArray();
                    for (int x = 0; x < max; x++) blocks[y][z][x] = x < row.size() ? row.get(x).getAsString() : "";
                }
            }
            return new Blueprint(o.has("name") ? o.get("name").getAsString() : name, o.get("offset").getAsInt(), blocks);
        } catch (Exception e) {
            return null;
        }
    }
}
