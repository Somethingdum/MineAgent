package com.mineagent.tools.crafting;

import com.google.gson.JsonObject;
import com.mineagent.api.agent.tool.Schema;
import com.mineagent.api.agent.tool.Tool;
import com.mineagent.api.agent.tool.ToolArgs;
import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.TaskDispatch;
import com.mineagent.engine.task.ProductionTask;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Make any amount of an item the most efficient way, start to finish (SMH plan 70: "If I ask for an entire chest of
 * stone bricks, it should find the most efficient way to do that. And be able to do that."): the production planner
 * weighs what it has, its chests, mining (and Silk Touch), the game's recipes, furnaces and fuel, then the task does it
 * all and puts the result in a chest or keeps it.
 */
public final class ProduceTool implements Tool {
    @Override public String name() { return "produce"; }

    @Override
    public String description() {
        return """
            Make any amount of an item, start to finish, the most efficient way: it plans from the game's
            own recipes and drops (what it carries and has in chests first, then mining, crafting, smelting
            in as many furnaces as pay off, the cheapest fuel, tools made as they wear out) and does it all.
            Use for "fill this chest with stone bricks", "make me 64 torches", "I need 3 iron pickaxes".
            count 0 with a chest fills that chest. Returns a task_id; it reports the plan and progress itself.
            """;
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Schema.object()
                .string("item_id", "Item ID to make, e.g. 'minecraft:stone_bricks'")
                .integer("count", "How many (0 with a chest: as many as fill it)", 0, 3456)
                .optionalInteger("chest_x", "Chest to put them in (x)", -30_000_000, 30_000_000)
                .optionalInteger("chest_y", "Chest to put them in (y)", -2048, 2048)
                .optionalInteger("chest_z", "Chest to put them in (z)", -30_000_000, 30_000_000)
                .build();
    }

    @Override
    public void onServerCall(String toolCallId, JsonObject args, AgentPlayer player, Consumer<String> reply) {
        String item = ToolArgs.getString(args, "item_id");
        var id = item == null ? null : ResourceLocation.tryParse(item);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            reply.accept(ToolArgs.errorJson("Unknown item: " + item));
            return;
        }
        Integer count = ToolArgs.getIntOrNull(args, "count");
        if (count == null || count < 0) {
            reply.accept(ToolArgs.errorJson("'count' must be 0 or more."));
            return;
        }
        BlockPos chest = null;
        if (ToolArgs.has(args, "chest_x") && ToolArgs.has(args, "chest_y") && ToolArgs.has(args, "chest_z")) {
            Integer x = ToolArgs.getIntOrNull(args, "chest_x"), y = ToolArgs.getIntOrNull(args, "chest_y"),
                    z = ToolArgs.getIntOrNull(args, "chest_z");
            if (x != null && y != null && z != null) chest = new BlockPos(x, y, z);
        }
        if (count == 0 && chest == null) {
            reply.accept(ToolArgs.errorJson("count 0 means 'fill a chest': give the chest's coordinates."));
            return;
        }
        TaskDispatch.dispatchAsync(player, new ProductionTask.ProduceRecord(toolCallId, id.toString(), count, chest), reply);
    }
}
