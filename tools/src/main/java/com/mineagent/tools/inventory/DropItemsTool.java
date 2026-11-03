package com.mineagent.tools.inventory;

import com.google.gson.JsonObject;
import com.mineagent.api.agent.tool.Schema;
import com.mineagent.api.agent.tool.Tool;
import com.mineagent.api.agent.tool.ToolArgs;
import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.engine.entity.CompanionEntity;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Drop items from inventory. The companion will drop the specified
 * items onto the ground at its current position.
 *
 * <p>This is a <b>sync</b> tool — replies immediately.
 */
public class DropItemsTool implements Tool {

    private static final int MAX_DROP_COUNT = 64;

    @Override
    public String name() { return "drop_items"; }

    @Override
    public String description() {
        return """
            Drop items from inventory onto the ground. Specify the item ID
            and count. The items are dropped at the companion's current position.
            """;
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Schema.object()
                .string("item_id", "Item ID to drop (e.g. 'minecraft:cobblestone')")
                .integer("count", "Number of items to drop (1-64)", 1, 64)
                .build();
    }

    @Override
    public void onServerCall(String toolCallId, JsonObject args, AgentPlayer player,
                              Consumer<String> reply) {
        String itemId = ToolArgs.getString(args, "item_id");
        if (itemId == null) {
            reply.accept("{\"error\":\"Missing required parameter 'item_id'.\"}");
            return;
        }
        if (!ToolArgs.has(args, "count")) {
            reply.accept("{\"error\":\"Missing required parameter 'count'.\"}");
            return;
        }
        var parsedItemId = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        if (parsedItemId == null
                || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(parsedItemId)) {
            reply.accept(ToolArgs.errorJson("Unknown item: " + itemId));
            return;
        }
        itemId = parsedItemId.toString();
        Integer count = ToolArgs.getIntOrNull(args, "count");
        if (count == null || count < 1 || count > MAX_DROP_COUNT) {
            reply.accept(ToolArgs.errorJson("'count' must be an integer from 1 to " + MAX_DROP_COUNT + "."));
            return;
        }

        var sp = ((CompanionEntity) player).serverPlayer();
        // An AFK owner picks nothing up, and dropped items vanish after 5 minutes (SMH plan 70, MA5.4).
        var loop = com.mineagent.engine.task.TaskContext.agentLoop(player);
        var owner = ((CompanionEntity) player).serverPlayerOwner();
        if (loop != null && loop.ownerAfk() && owner != null && owner.serverLevel() == sp.serverLevel()
                && owner.distanceTo(sp) <= 8.0) {
            // Into a chest by them instead: one it knows within 24 blocks of them, or one in sight within 12.
            var chest = chestFor(loop, owner);
            if (chest == null) {
                reply.accept(ToolArgs.errorJson("The owner is AFK: dropped items would vanish before they're back, and "
                        + "there's no chest near them. Keep them and hand them over when they're back."));
                return;
            }
            com.mineagent.api.task.TaskDispatch.dispatchAsync(player, new com.mineagent.engine.task.StoreItemsTask.StoreRecord(
                    toolCallId, itemId, count, chest.getX(), chest.getY(), chest.getZ()), reply);
            return;
        }
        var inv = sp.getInventory();
        int carriedBefore = countItem(inv, itemId);

        // Find and count matching items
        int remaining = count;
        int dropped = 0;

        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            var stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (!id.equals(itemId)) continue;

            int toDrop = Math.min(remaining, stack.getCount());
            var dropStack = stack.split(toDrop);
            var droppedEntity = sp.drop(dropStack, false, true);
            if (droppedEntity == null) {
                // Player#drop is normally guaranteed on the logical server,
                // but restoring the split stack keeps item conservation if a
                // platform hook vetoes entity creation.
                stack.grow(toDrop);
                break;
            }
            remaining -= toDrop;
            dropped += toDrop;

            if (stack.isEmpty()) {
                inv.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
            }
        }

        if (dropped == 0) {
            reply.accept(ToolArgs.errorJson("Item '" + itemId + "' not found in inventory."));
        } else {
            // Publish both the normal inventory menu and any currently open
            // container menu. Updating only containerMenu leaves the owner's
            // hotbar stale when a platform keeps a separate inventory menu,
            // which then makes the next planning snapshot reason from items
            // that were already dropped.
            com.mineagent.engine.task.TaskContext.syncInventory(sp);
            JsonObject result = new JsonObject();
            int carriedAfter = countItem(inv, itemId);
            result.addProperty("success", true);
            result.addProperty("item", itemId);
            result.addProperty("dropped", dropped);
            result.addProperty("requested", count);
            result.addProperty("carried_before", carriedBefore);
            result.addProperty("carried_after", carriedAfter);
            // Re-count after the ItemEntity was actually created. A callback
            // alone is not enough because platform hooks can veto Player#drop.
            result.addProperty("postcondition_verified",
                    carriedAfter == carriedBefore - dropped);
            reply.accept(result.toString());
        }
    }

    /** A chest by an AFK owner for what was meant for them (SMH plan 70, MA5.4); null if there is none. */
    static net.minecraft.core.BlockPos chestFor(com.mineagent.engine.loop.AgentLoop loop,
                                                net.minecraft.server.level.ServerPlayer owner) {
        var level = owner.serverLevel();
        String dim = level.dimension().location().toString();
        for (var c : loop.chestMemory().near(dim, owner.getBlockX(), owner.getBlockY(), owner.getBlockZ(), 24.0)) {
            var pos = new net.minecraft.core.BlockPos(c.x(), c.y(), c.z());
            if (level.isLoaded(pos) && net.minecraft.world.level.block.entity.HopperBlockEntity.getContainerAt(level, pos) != null
                    && isChest(level.getBlockState(pos))) return pos;
        }
        net.minecraft.core.BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        var at = owner.blockPosition();
        for (int dx = -12; dx <= 12; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -12; dz <= 12; dz++) {
                    var pos = at.offset(dx, dy, dz);
                    if (!level.isLoaded(pos) || !isChest(level.getBlockState(pos))) continue;
                    double d = dx * dx + dy * dy + dz * dz;
                    if (d < bestD) {
                        bestD = d;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    private static boolean isChest(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(net.minecraft.world.level.block.Blocks.CHEST) || state.is(net.minecraft.world.level.block.Blocks.TRAPPED_CHEST)
                || state.is(net.minecraft.world.level.block.Blocks.BARREL);
    }

    private static int countItem(net.minecraft.world.entity.player.Inventory inventory,
                                 String itemId) {
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            if (!stack.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(stack.getItem()).toString().equals(itemId)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
