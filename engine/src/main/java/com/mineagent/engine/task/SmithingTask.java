package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.act.Interaction;
import com.mineagent.engine.act.Placement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Diamond gear to netherite at a smithing table (SMH plan 70: "gear that makes a Minecraft sweat blush"), as a player
 * does it: the upgrade template, the diamond piece and a netherite ingot in their slots, the netherite piece taken. Its
 * enchantments and repairs go with it. A table near is used, else one from its pack is put down, else one is made (two
 * iron, four planks). One template and one ingot for each piece; it says what it lacks.
 */
public class SmithingTask extends CompanionTask<SmithingTask.SmithRecord> {
    public static final class SmithRecord extends TaskRecord {
        /** The kind of piece ("sword", "chestplate"), "armor" for its four pieces, or "gear" for every diamond piece it can. */
        public final String what;

        public SmithRecord(String toolCallId, String what) {
            super(toolCallId);
            this.what = what == null || what.isBlank() ? "gear" : what;
        }
    }

    private enum Phase { SITE, OPEN, WORK, DONE }

    private Phase phase = Phase.SITE;
    private BlockPos table;
    private ProductionTask sub;
    private int tries;
    private final List<String> upgraded = new ArrayList<>();
    private String outcome = "";

    public SmithingTask(AgentPlayer player, SmithRecord record) {
        super(player, record);
    }

    @Override
    protected void onStart() {
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        ServerLevel level = sp.serverLevel();
        try {
            switch (phase) {
                case SITE -> {
                    if (piece(sp) < 0) return end(false, "I have no diamond " + ("gear".equals(record.what) ? "gear" : record.what) + " to upgrade");
                    if (ProductionTask.count(sp, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) == 0) {
                        return end(false, "I have no netherite upgrade template (bastions have them; one copies with 7 diamonds and netherrack)");
                    }
                    if (ProductionTask.count(sp, Items.NETHERITE_INGOT) == 0) {
                        return end(false, "I have no netherite ingot (four netherite scrap, from ancient debris, and four gold)");
                    }
                    table = near(level, sp);
                    if (table == null) {
                        if (ProductionTask.count(sp, Items.SMITHING_TABLE) == 0) {
                            if (sub == null) {
                                sub = new ProductionTask(player, new ProductionTask.ProduceRecord(record.toolCallId() + ":table",
                                        "minecraft:smithing_table", 1, null, false, true));
                                sub.start();
                            }
                            TaskState st = sub.tick();
                            if (st == TaskState.RUNNING) return TaskState.RUNNING;
                            sub = null;
                            if (ProductionTask.count(sp, Items.SMITHING_TABLE) == 0) return end(false, "I have no smithing table and couldn't make one");
                        }
                        BlockPos here = ProductionTask.standing(sp);
                        for (int[] o : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}}) {
                            BlockPos p = here.offset(o[0], 0, o[1]);
                            if (level.getBlockState(p).isAir() && Placement.placeBlock(sp, p, Blocks.SMITHING_TABLE.defaultBlockState())) {
                                table = p;
                                break;
                            }
                        }
                        if (table == null) {
                            if (++tries > 40) return end(false, "I couldn't put the smithing table down");
                            return TaskState.RUNNING;
                        }
                    }
                    phase = Phase.OPEN;
                    tries = 0;
                    return TaskState.RUNNING;
                }
                case OPEN -> {
                    if (sp.containerMenu instanceof SmithingMenu) {
                        phase = Phase.WORK;
                        return TaskState.RUNNING;
                    }
                    if (!level.getBlockState(table).is(Blocks.SMITHING_TABLE)) return end(false, "the smithing table is gone");
                    if (++tries > 40) return end(false, "I couldn't open the smithing table");
                    if (tries % 5 == 1) Interaction.interactBlock(sp, table, InteractionHand.MAIN_HAND);
                    return TaskState.RUNNING;
                }
                case WORK -> {
                    if (!(sp.containerMenu instanceof SmithingMenu menu)) return end(!upgraded.isEmpty(), upgraded.isEmpty() ? "the smithing table closed" : null);
                    int at = piece(sp);
                    int template = slotOf(sp, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), ingot = slotOf(sp, Items.NETHERITE_INGOT);
                    if (at < 0 || template < 0 || ingot < 0) {
                        String why = at < 0 ? null : template < 0 ? "no template left for the rest" : "no netherite ingot left for the rest";
                        return end(!upgraded.isEmpty(), why);
                    }
                    var inv = sp.getInventory();
                    String was = BuiltInRegistries.ITEM.getKey(inv.getItem(at).getItem()).getPath().replace('_', ' ');
                    menu.getSlot(0).set(inv.removeItem(template, 1));
                    menu.getSlot(1).set(inv.removeItem(at, 1));
                    menu.getSlot(2).set(inv.removeItem(ingot, 1));
                    ItemStack result = menu.getSlot(3).getItem();
                    if (result.isEmpty()) {
                        back(sp, menu);
                        return end(!upgraded.isEmpty(), "the table wouldn't upgrade my " + was);
                    }
                    menu.clicked(3, 0, ClickType.QUICK_MOVE, sp);
                    upgraded.add(was);
                    back(sp, menu);
                    TaskContext.syncInventory(sp);
                    if (!"gear".equals(record.what) && !"armor".equals(record.what)) return end(true, null);
                    return TaskState.RUNNING;
                }
                default -> {
                    return end(true, null);
                }
            }
        } catch (RuntimeException e) {
            return end(false, "something went wrong: " + e.getMessage());
        }
    }

    /** What is left in the table's input slots, back into its pack. */
    private static void back(ServerPlayer sp, SmithingMenu menu) {
        for (int i = 0; i < 3; i++) {
            ItemStack st = menu.getSlot(i).getItem();
            if (st.isEmpty()) continue;
            menu.getSlot(i).set(ItemStack.EMPTY);
            if (!sp.getInventory().add(st)) sp.drop(st, false);
        }
    }

    /** The pack slot of a diamond piece of the kind asked for (any kind for "gear"), or -1. */
    private int piece(ServerPlayer sp) {
        var inv = sp.getInventory();
        for (int i = 0; i < Math.min(36, inv.getContainerSize()); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
            if (!id.startsWith("minecraft:diamond_")) continue;
            String kind = com.mineagent.engine.gear.GodGear.kindOf(id);
            if (kind == null) continue;
            String what = record.what.replace(' ', '_');
            boolean armor = java.util.Set.of("helmet", "chestplate", "leggings", "boots").contains(kind);
            if ("gear".equals(what) || "armor".equals(what) && armor || kind.equals(what) || kind.equals(what + "s")) return i;
        }
        return -1;
    }

    private static int slotOf(ServerPlayer sp, net.minecraft.world.item.Item item) {
        var inv = sp.getInventory();
        for (int i = 0; i < Math.min(36, inv.getContainerSize()); i++) if (inv.getItem(i).is(item)) return i;
        return -1;
    }

    private static BlockPos near(ServerLevel level, ServerPlayer sp) {
        BlockPos at = sp.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-4, -2, -4), at.offset(4, 2, 4))) {
            if (level.getBlockState(p).is(Blocks.SMITHING_TABLE) && p.distToCenterSqr(sp.getEyePosition()) <= 4.0 * 4.0) return p.immutable();
        }
        return null;
    }

    private TaskState end(boolean ok, String why) {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        if (sp.containerMenu instanceof SmithingMenu menu) {
            back(sp, menu);
            sp.closeContainer();
        }
        if (sub != null) sub.interrupt();
        sub = null;
        phase = Phase.DONE;
        outcome = upgraded.isEmpty() ? (why == null ? "nothing to upgrade" : why)
                : "Upgraded my " + String.join(", ", upgraded) + " to netherite" + (why == null ? "" : "; " + why);
        var loop = TaskContext.agentLoop(player);
        if (loop != null) loop.say(upgraded.isEmpty() ? "I couldn't upgrade to netherite: " + outcome + "." : outcome + ".");
        return ok ? TaskState.SUCCESS : TaskState.FAILED;
    }

    @Override
    protected String successMessage() {
        return "{\"success\":true,\"note\":\"" + outcome.replace("\"", "'") + "\"}";
    }

    @Override
    protected String failureMessage() {
        return "{\"success\":false,\"note\":\"" + outcome.replace("\"", "'") + "\"}";
    }

    @Override
    protected void onInterrupt() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        if (sp.containerMenu instanceof SmithingMenu menu) {
            back(sp, menu);
            sp.closeContainer();
        }
        if (sub != null) sub.interrupt();
        sub = null;
    }
}
