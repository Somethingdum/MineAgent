package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.act.Interaction;
import com.mineagent.engine.act.Placement;
import com.mineagent.engine.pathing.util.BlockHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Enchanting (SMH plan 70, MA11.2): an enchanting table put down with up to 15 bookshelves round it (the ring two blocks
 * out, one gap left to walk in by), then the best piece of gear it has enchanted at the best level its experience and
 * lapis allow, through the table's own menu as a player uses it. Best gear first: netherite, then diamond, then iron;
 * a pickaxe or sword before armour. Anvils, grindstones and books from librarians are not built yet.
 */
public class EnchantTask extends CompanionTask<EnchantTask.EnchantRecord> {
    public static final class EnchantRecord extends TaskRecord {
        /** What to enchant ("pickaxe", "sword", "gear"): a word in the item's name, or "gear" for the best it has. */
        public final String what;

        public EnchantRecord(String toolCallId, String what) {
            super(toolCallId);
            this.what = what == null || what.isBlank() ? "gear" : what;
        }
    }

    private enum Phase { SITE, SHELVES, OPEN, ENCHANT, DONE, FAILED }

    private Phase phase = Phase.SITE;
    private BlockPos table;
    private final List<BlockPos> shelves = new ArrayList<>();
    private int index, tries;
    private String outcome = "";

    public EnchantTask(AgentPlayer player, EnchantRecord record) {
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
                    if (pick(sp) < 0) return end(false, "I have nothing to enchant" + ("gear".equals(record.what) ? "" : " called " + record.what));
                    if (ProductionTask.count(sp, Items.LAPIS_LAZULI) == 0) return end(false, "I have no lapis lazuli");
                    if (sp.experienceLevel < 1) return end(false, "I have no experience to spend");
                    table = nearTable(level, sp);
                    if (table == null) {
                        if (ProductionTask.count(sp, Items.ENCHANTING_TABLE) == 0) return end(false, "I have no enchanting table and know of none near");
                        BlockPos spot = siteFor(level, sp);
                        if (spot == null) return end(false, "there is no flat, open ground round me for the table and its shelves");
                        if (!Placement.placeBlock(sp, spot, Blocks.ENCHANTING_TABLE.defaultBlockState())) {
                            if (++tries > 40) return end(false, "I couldn't put the enchanting table down");
                            return TaskState.RUNNING;
                        }
                        table = spot;
                    }
                    shelves.clear();
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            if (Math.abs(dx) != 2 && Math.abs(dz) != 2) continue;
                            if (dx == 0 && dz == 2) continue; // the way in
                            shelves.add(table.offset(dx, 0, dz));
                        }
                    }
                    phase = Phase.SHELVES;
                    index = 0;
                    tries = 0;
                    return TaskState.RUNNING;
                }
                case SHELVES -> {
                    while (index < shelves.size() && ProductionTask.count(sp, Items.BOOKSHELF) > 0) {
                        BlockPos p = shelves.get(index);
                        var st = level.getBlockState(p);
                        if (st.is(Blocks.BOOKSHELF) || !st.isAir() && !st.canBeReplaced()) {
                            index++;
                            continue;
                        }
                        if (Placement.placeBlock(sp, p, Blocks.BOOKSHELF.defaultBlockState())) {
                            index++;
                            tries = 0;
                            return TaskState.RUNNING;
                        }
                        if (++tries > 30) {
                            index++; // that one can't go in from here: fewer shelves, a lower level
                            tries = 0;
                        }
                        return TaskState.RUNNING;
                    }
                    phase = Phase.OPEN;
                    tries = 0;
                    return TaskState.RUNNING;
                }
                case OPEN -> {
                    if (sp.containerMenu instanceof EnchantmentMenu) {
                        phase = Phase.ENCHANT;
                        return TaskState.RUNNING;
                    }
                    if (++tries > 40) return end(false, "I couldn't open the enchanting table");
                    if (tries % 5 == 1) Interaction.interactBlock(sp, table, InteractionHand.MAIN_HAND);
                    return TaskState.RUNNING;
                }
                case ENCHANT -> {
                    return enchant(sp);
                }
                default -> {
                    return phase == Phase.DONE ? TaskState.SUCCESS : TaskState.FAILED;
                }
            }
        } catch (RuntimeException e) {
            return end(false, "something went wrong: " + e.getMessage());
        }
    }

    /** The item in, the lapis in, the best option it can pay for, and both taken back. */
    private TaskState enchant(ServerPlayer sp) {
        if (!(sp.containerMenu instanceof EnchantmentMenu menu)) return end(false, "the enchanting table closed");
        var inv = sp.getInventory();
        int slot = pick(sp);
        if (slot < 0) return close(sp, false, "I have nothing to enchant");
        ItemStack gear = inv.removeItem(slot, 1);
        String name = BuiltInRegistries.ITEM.getKey(gear.getItem()).getPath().replace('_', ' ');
        menu.getSlot(0).set(gear);
        int lapis = 0;
        for (int i = 0; i < inv.getContainerSize() && lapis < 3; i++) {
            if (!inv.getItem(i).is(Items.LAPIS_LAZULI)) continue;
            ItemStack l = inv.removeItem(i, Math.min(3 - lapis, inv.getItem(i).getCount()));
            lapis += l.getCount();
        }
        menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, lapis));
        int best = -1;
        for (int i = 2; i >= 0; i--) {
            int cost = menu.costs[i];
            if (cost > 0 && sp.experienceLevel >= cost && lapis >= i + 1) {
                best = i;
                break;
            }
        }
        boolean done = best >= 0 && menu.clickMenuButton(sp, best);
        int cost = best >= 0 ? menu.costs[best] : 0;
        // Both back in its pack.
        ItemStack out = menu.getSlot(0).getItem().copy();
        ItemStack left = menu.getSlot(1).getItem().copy();
        menu.getSlot(0).set(ItemStack.EMPTY);
        menu.getSlot(1).set(ItemStack.EMPTY);
        if (!out.isEmpty() && !inv.add(out)) sp.drop(out, false);
        if (!left.isEmpty() && !inv.add(left)) sp.drop(left, false);
        TaskContext.syncInventory(sp);
        if (!done) return close(sp, false, "the table offers nothing I can pay for (" + sp.experienceLevel + " levels, " + lapis + " lapis, "
                + shelvesThere(sp.serverLevel()) + " bookshelves)");
        return close(sp, true, "Enchanted my " + name + " at level " + cost + " (" + shelvesThere(sp.serverLevel()) + " bookshelves).");
    }

    private int shelvesThere(ServerLevel level) {
        int n = 0;
        for (BlockPos p : shelves) if (level.getBlockState(p).is(Blocks.BOOKSHELF)) n++;
        return n;
    }

    /** The best piece to enchant: the slot, or -1. Not enchanted already; best material first, tools before armour. */
    private int pick(ServerPlayer sp) {
        var inv = sp.getInventory();
        int best = -1, bestScore = -1;
        for (int i = 0; i < Math.min(36, inv.getContainerSize()); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty() || st.isEnchanted() || !st.isEnchantable()) continue;
            String id = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
            if (!"gear".equals(record.what) && !id.contains(record.what.replace(' ', '_'))) continue;
            int score = (id.startsWith("netherite_") ? 50 : id.startsWith("diamond_") ? 40 : id.startsWith("iron_") ? 30
                    : id.startsWith("stone_") ? 20 : 10) + (id.endsWith("_pickaxe") || id.endsWith("_sword") ? 5 : id.endsWith("_axe") ? 3 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    /** An enchanting table within 8 blocks, or null. */
    private static BlockPos nearTable(ServerLevel level, ServerPlayer sp) {
        BlockPos at = sp.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-8, -3, -8), at.offset(8, 3, 8))) {
            if (level.getBlockState(p).is(Blocks.ENCHANTING_TABLE)) return p.immutable();
        }
        return null;
    }

    /**
     * A spot for the table beside it, so it stands inside the ring of shelves (one block from the table): the 5 by 5
     * round the table flat and open two high, no one else in it.
     */
    private static BlockPos siteFor(ServerLevel level, ServerPlayer sp) {
        BlockPos here = ProductionTask.standing(sp);
        int[][] beside = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        for (int[] o : beside) {
            BlockPos c = here.offset(o[0], 0, o[1]);
            boolean ok = true;
            for (int x = -2; x <= 2 && ok; x++) {
                for (int z = -2; z <= 2 && ok; z++) {
                    BlockPos g = c.offset(x, 0, z);
                    ok = BlockHelper.canStandOn(level.getBlockState(g.below())) && level.getBlockState(g).isAir()
                            && level.getBlockState(g.above()).isAir();
                }
            }
            if (!ok || !level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                    new net.minecraft.world.phys.AABB(c).inflate(2.5, 1, 2.5), e -> e != sp).isEmpty()) continue;
            return c;
        }
        return null;
    }

    private TaskState close(ServerPlayer sp, boolean ok, String line) {
        sp.closeContainer();
        return end(ok, line);
    }

    private TaskState end(boolean ok, String line) {
        TaskContext.inputDriver(player).clear();
        outcome = line;
        var loop = TaskContext.agentLoop(player);
        if (loop != null) loop.say(ok ? line : "I couldn't enchant anything: " + line + ".");
        phase = ok ? Phase.DONE : Phase.FAILED;
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
        TaskContext.inputDriver(player).clear();
        var sp = TaskContext.serverPlayer(player);
        if (sp != null && sp.containerMenu instanceof EnchantmentMenu) sp.closeContainer();
    }
}
