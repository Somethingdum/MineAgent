package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.act.Placement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

import java.util.List;
import java.util.Map;

/**
 * Brewing (SMH plan 70, MA11.4): the potions that keep it alive (healing, fire resistance, strength, swiftness), from
 * water bottles: nether wart for awkward potions, then the ingredient. A brewing stand put down (or one near), blaze
 * powder for its fuel, the bottles and each ingredient in turn, as a player loads it; the potions taken out when done.
 */
public class BrewTask extends CompanionTask<BrewTask.BrewRecord> {
    public static final class BrewRecord extends TaskRecord {
        /** "fire_resistance", "healing", "strength", "swiftness". */
        public final String potion;

        public BrewRecord(String toolCallId, String potion) {
            super(toolCallId);
            this.potion = potion;
        }
    }

    /** The ingredient that turns an awkward potion into each one it brews. */
    public static final Map<String, Item> INGREDIENT = Map.of(
            "fire_resistance", Items.MAGMA_CREAM, "healing", Items.GLISTERING_MELON_SLICE,
            "strength", Items.BLAZE_POWDER, "swiftness", Items.SUGAR, "slow_falling", Items.PHANTOM_MEMBRANE,
            "regeneration", Items.GHAST_TEAR, "night_vision", Items.GOLDEN_CARROT, "water_breathing", Items.PUFFERFISH,
            "leaping", Items.RABBIT_FOOT);

    private static final Map<String, Holder<Potion>> RESULT = Map.of(
            "fire_resistance", Potions.FIRE_RESISTANCE, "healing", Potions.HEALING,
            "strength", Potions.STRENGTH, "swiftness", Potions.SWIFTNESS, "slow_falling", Potions.SLOW_FALLING,
            "regeneration", Potions.REGENERATION, "night_vision", Potions.NIGHT_VISION, "water_breathing", Potions.WATER_BREATHING,
            "leaping", Potions.LEAPING);

    private enum Phase { FILL, STAND, LOAD, BREW, TAKE, DONE, FAILED }

    private Phase phase = Phase.STAND;
    /** Its empty bottles filled at water near before it brews (the ingredients got itself, MA11.4). */
    private boolean filled;
    private BlockPos water;
    private int fillTicks;
    private com.mineagent.engine.pathing.execute.PlayerNav nav;
    private BlockPos stand;
    private int step, tries, bottles;
    private long brewStart;
    private String outcome = "";

    public BrewTask(AgentPlayer player, BrewRecord record) {
        super(player, record);
    }

    @Override
    protected void onStart() {
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        ServerLevel level = sp.serverLevel();
        Item ingredient = INGREDIENT.get(record.potion);
        if (ingredient == null) return end(false, "I don't know how to brew " + record.potion.replace('_', ' '));
        try {
            switch (phase) {
                case FILL -> {
                    if (nav == null) nav = new com.mineagent.engine.pathing.execute.PlayerNav(player, TaskContext.navCaches(player));
                    if (waterBottles(sp) >= 3 || ProductionTask.count(sp, Items.GLASS_BOTTLE) == 0 || ++fillTicks > 1_200) {
                        filled = true;
                        nav.cancel();
                        TaskContext.inputDriver(player).clear();
                        phase = Phase.STAND;
                        return TaskState.RUNNING;
                    }
                    if (water == null || !level.getFluidState(water).isSourceOfType(net.minecraft.world.level.material.Fluids.WATER)) water = nearWater(level, sp);
                    if (water == null) {
                        filled = true;
                        phase = Phase.STAND;
                        return TaskState.RUNNING;
                    }
                    if (water.distToCenterSqr(sp.getEyePosition()) > 4.0 * 4.0) {
                        if (fillTicks % 20 == 1) nav.navigateNear(water.getX(), water.getY(), water.getZ(), 2);
                        nav.tick();
                        return TaskState.RUNNING;
                    }
                    nav.cancel();
                    TaskContext.inputDriver(player).clear();
                    if (TaskContext.selectInventoryItemForHand(player, "minecraft:glass_bottle", net.minecraft.world.InteractionHand.MAIN_HAND)) {
                        sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, net.minecraft.world.phys.Vec3.atCenterOf(water).add(0, 0.4, 0));
                        com.mineagent.engine.act.Interaction.useItem(sp, net.minecraft.world.InteractionHand.MAIN_HAND);
                        TaskContext.syncInventory(sp);
                    }
                    return TaskState.RUNNING;
                }
                case STAND -> {
                    if (!filled && waterBottles(sp) < 3 && ProductionTask.count(sp, Items.GLASS_BOTTLE) > 0) {
                        phase = Phase.FILL; // empty bottles: filled at water near first
                        return TaskState.RUNNING;
                    }
                    if (waterBottles(sp) == 0) return end(false, "I have no water bottles" + (filled ? " and no water near to fill some" : ""));
                    if (ProductionTask.count(sp, Items.NETHER_WART) == 0) return end(false, "I have no nether wart");
                    if (ProductionTask.count(sp, ingredient) == 0) return end(false, "I have no " + name(ingredient));
                    if (ProductionTask.count(sp, Items.BLAZE_POWDER) == 0) return end(false, "I have no blaze powder for the stand's fuel");
                    stand = nearStand(level, sp);
                    if (stand == null) {
                        if (ProductionTask.count(sp, Items.BREWING_STAND) == 0) return end(false, "I have no brewing stand and know of none near");
                        BlockPos here = ProductionTask.standing(sp);
                        for (int[] o : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {2, 0}, {0, 2}}) {
                            BlockPos p = here.offset(o[0], 0, o[1]);
                            if (level.getBlockState(p).isAir() && Placement.placeBlock(sp, p, Blocks.BREWING_STAND.defaultBlockState())) {
                                stand = p;
                                break;
                            }
                        }
                        if (stand == null) {
                            if (++tries > 40) return end(false, "I couldn't put the brewing stand down");
                            return TaskState.RUNNING;
                        }
                    }
                    phase = Phase.LOAD;
                    step = 0;
                    return TaskState.RUNNING;
                }
                case LOAD -> {
                    if (!(level.getBlockEntity(stand) instanceof BrewingStandBlockEntity be)) return end(false, "the brewing stand is gone");
                    if (!sp.canInteractWithBlock(stand, 0.0)) return end(false, "the brewing stand is out of my reach");
                    var inv = sp.getInventory();
                    // Fuel: one blaze powder is twenty brews.
                    if (be.getItem(4).isEmpty()) move(sp, be, Items.BLAZE_POWDER, 1, 4);
                    if (step == 0) {
                        // Up to three water bottles.
                        bottles = 0;
                        for (int slot = 0; slot < 3; slot++) {
                            if (!be.getItem(slot).isEmpty()) continue;
                            for (int i = 0; i < inv.getContainerSize(); i++) {
                                ItemStack st = inv.getItem(i);
                                if (isWater(st)) {
                                    be.setItem(slot, inv.removeItem(i, 1));
                                    break;
                                }
                            }
                        }
                        for (int slot = 0; slot < 3; slot++) if (!be.getItem(slot).isEmpty()) bottles++;
                        if (bottles == 0) return end(false, "no bottles went in");
                    }
                    Item next = step == 0 ? Items.NETHER_WART : ingredient;
                    if (!move(sp, be, next, 1, 3)) return end(false, "I have no " + name(next) + " left");
                    be.setChanged();
                    TaskContext.syncInventory(sp);
                    brewStart = level.getGameTime();
                    phase = Phase.BREW;
                    return TaskState.RUNNING;
                }
                case BREW -> {
                    if (!(level.getBlockEntity(stand) instanceof BrewingStandBlockEntity be)) return end(false, "the brewing stand is gone");
                    // The ingredient is used up when a brew is done (400 ticks).
                    if (be.getItem(3).isEmpty()) {
                        if (step == 0) {
                            step = 1;
                            phase = Phase.LOAD;
                        } else {
                            phase = Phase.TAKE;
                        }
                        return TaskState.RUNNING;
                    }
                    if (level.getGameTime() - brewStart > 1_200) return end(false, "the brewing stand didn't brew (is it fuelled?)");
                    return TaskState.RUNNING;
                }
                case TAKE -> {
                    if (!(level.getBlockEntity(stand) instanceof BrewingStandBlockEntity be)) return end(false, "the brewing stand is gone");
                    int made = 0;
                    for (int slot = 0; slot < 3; slot++) {
                        ItemStack st = be.getItem(slot);
                        if (st.isEmpty()) continue;
                        var c = st.get(DataComponents.POTION_CONTENTS);
                        if (c != null && c.is(RESULT.get(record.potion))) made++;
                        if (!sp.getInventory().add(be.removeItem(slot, 1))) sp.drop(st, false);
                    }
                    be.setChanged();
                    TaskContext.syncInventory(sp);
                    if (made == 0) return end(false, "the potions came out wrong");
                    return end(true, "Brewed " + made + " potion" + (made == 1 ? "" : "s") + " of " + record.potion.replace('_', ' ') + ".");
                }
                default -> {
                    return phase == Phase.DONE ? TaskState.SUCCESS : TaskState.FAILED;
                }
            }
        } catch (RuntimeException e) {
            return end(false, "something went wrong: " + e.getMessage());
        }
    }

    /** One of {@code item} from its pack into the stand's slot; false when it has none. */
    private static boolean move(ServerPlayer sp, BrewingStandBlockEntity be, Item item, int n, int slot) {
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (!inv.getItem(i).is(item)) continue;
            ItemStack in = inv.removeItem(i, n);
            ItemStack there = be.getItem(slot);
            if (there.isEmpty()) be.setItem(slot, in);
            else there.grow(in.getCount());
            return true;
        }
        return false;
    }

    private static boolean isWater(ItemStack st) {
        if (!st.is(Items.POTION)) return false;
        var c = st.get(DataComponents.POTION_CONTENTS);
        return c != null && c.is(Potions.WATER);
    }

    /** Still water within 16 blocks, open above, to fill a bottle from. */
    private static BlockPos nearWater(ServerLevel level, ServerPlayer sp) {
        BlockPos at = sp.blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-16, -4, -16), at.offset(16, 4, 16))) {
            if (!level.getFluidState(p).isSourceOfType(net.minecraft.world.level.material.Fluids.WATER) || !level.getBlockState(p.above()).isAir()) continue;
            double d = p.distSqr(at);
            if (d < bestD) {
                bestD = d;
                best = p.immutable();
            }
        }
        return best;
    }

    private static int waterBottles(ServerPlayer sp) {
        int n = 0;
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (isWater(inv.getItem(i))) n += inv.getItem(i).getCount();
        return n;
    }

    private static BlockPos nearStand(ServerLevel level, ServerPlayer sp) {
        BlockPos at = sp.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-3, -2, -3), at.offset(3, 2, 3))) {
            if (level.getBlockState(p).is(Blocks.BREWING_STAND)) return p.immutable();
        }
        return null;
    }

    private static String name(Item item) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath().replace('_', ' ');
    }

    /** A water bottle, for the scenes and for filling bottles. */
    public static ItemStack waterBottle() {
        return PotionContents.createItemStack(Items.POTION, Potions.WATER);
    }

    private TaskState end(boolean ok, String line) {
        TaskContext.inputDriver(player).clear();
        outcome = line;
        var loop = TaskContext.agentLoop(player);
        if (loop != null) loop.say(ok ? line : "I couldn't brew: " + line + ".");
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
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
    }

    /** The potions it knows how to brew, for the parser. */
    public static List<String> potions() {
        return List.copyOf(INGREDIENT.keySet());
    }
}
