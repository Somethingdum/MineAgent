package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.pathing.execute.PlayerNav;
import com.mineagent.engine.world.ChestPurposes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Putting things away by purpose (SMH plan 70, MA9.2): what it carries and doesn't need for its work (its best tool of
 * each kind, 16 food, 32 torches, its golden apples and, with a bow, 64 arrows stay) goes in the chest near whose purpose fits it; what has no chest of its own
 * goes to an overflow chest (one said to be for anything, else the nearest without a purpose), at most two. Tidying:
 * what lies in the overflow chests that a chest with a purpose takes is moved there. Chests within 32 blocks.
 */
public class PutAwayTask extends CompanionTask<PutAwayTask.PutAwayRecord> {
    public static final class PutAwayRecord extends TaskRecord {
        /** Only the tidying: the overflow chests sorted into the chests with a purpose. */
        public final boolean tidyOnly;

        public PutAwayRecord(String toolCallId, boolean tidyOnly) {
            super(toolCallId);
            this.tidyOnly = tidyOnly;
        }
    }

    private enum Phase { PLAN, VISIT, DONE, FAILED }

    static final int NEAR = 32, OVERFLOW = 2, KEEP_FOOD = 16, KEEP_TORCHES = 32, KEEP_ARROWS = 64;

    /** One stop: a chest, what it takes from there, what it puts in. */
    private record Visit(BlockPos chest, Map<String, Integer> take, Map<String, Integer> put) {}

    private Phase phase = Phase.PLAN;
    private PlayerNav nav;
    private final List<Visit> visits = new ArrayList<>();
    private int index, ticks, stored, moved;
    private final Set<BlockPos> full = new HashSet<>();
    private String outcome = "";

    public PutAwayTask(AgentPlayer player, PutAwayRecord record) {
        super(player, record);
    }

    @Override
    protected void onStart() {
        if (nav == null) nav = new PlayerNav(player, TaskContext.navCaches(player));
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        ServerLevel level = sp.serverLevel();
        ticks++;
        try {
            switch (phase) {
                case PLAN -> {
                    var loop = TaskContext.agentLoop(player);
                    if (loop == null) return end(false, "I have no memory of chests here");
                    String dim = level.dimension().location().toString();
                    // The chests near, with their purposes: the one its owner gave it, else what the labels on it say
                    // (signs and item frames: its owner's sorting system), several to a chest.
                    Map<BlockPos, List<String>> purpose = new LinkedHashMap<>();
                    boolean labels = com.mineagent.engine.settings.Switches.readChestLabels();
                    List<BlockPos> plain = new ArrayList<>();
                    BlockPos at = sp.blockPosition();
                    for (BlockPos p : BlockPos.betweenClosed(at.offset(-NEAR, -6, -NEAR), at.offset(NEAR, 6, NEAR))) {
                        if (!level.isLoaded(p) || !(level.getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.BaseContainerBlockEntity)) continue;
                        if (!(level.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.ChestBlock)
                                && !level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.BARREL)) continue;
                        // A double chest is one chest: its first half, the purpose set on either.
                        BlockPos one = p.immutable();
                        var pur = loop.chestPurposes().of(dim, p.getX(), p.getY(), p.getZ());
                        var st = level.getBlockState(p);
                        if (st.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
                                && st.getValue(net.minecraft.world.level.block.ChestBlock.TYPE) != net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
                            BlockPos other = p.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(st));
                            if (purpose.containsKey(other) || plain.contains(other)) continue;
                            if (pur.isEmpty()) pur = loop.chestPurposes().of(dim, other.getX(), other.getY(), other.getZ());
                        }
                        if (pur.isPresent()) {
                            purpose.put(one, List.of(pur.get()));
                            continue;
                        }
                        List<String> said = labels ? com.mineagent.engine.world.ChestLabels.read(level, one) : List.of();
                        if (!said.isEmpty()) purpose.put(one, said);
                        else plain.add(one);
                    }
                    if (purpose.isEmpty() && plain.isEmpty()) return end(false, "there's no chest near me");
                    plain.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(at)));
                    List<BlockPos> overflow = new ArrayList<>();
                    purpose.forEach((p, w) -> {
                        if (w.contains("overflow")) overflow.add(p);
                    });
                    for (BlockPos p : plain) if (overflow.size() < OVERFLOW) overflow.add(p);
                    Map<BlockPos, Map<String, Integer>> put = new LinkedHashMap<>();
                    Map<BlockPos, Map<String, Integer>> take = new LinkedHashMap<>();
                    if (!record.tidyOnly) {
                        for (var e : spare(sp).entrySet()) {
                            BlockPos to = chestFor(e.getKey(), purpose, overflow, at);
                            if (to != null) put.computeIfAbsent(to, k -> new LinkedHashMap<>()).merge(e.getKey(), e.getValue(), Integer::sum);
                        }
                    }
                    // Tidying: what lies in the overflow chests that a chest with a purpose takes.
                    for (BlockPos o : overflow) {
                        Container c = HopperBlockEntity.getContainerAt(level, o);
                        if (c == null) continue;
                        for (int i = 0; i < c.getContainerSize(); i++) {
                            ItemStack st = c.getItem(i);
                            if (st.isEmpty()) continue;
                            String id = id(st);
                            BlockPos to = chestFor(id, purpose, List.of(), at);
                            if (to == null || to.equals(o) || purpose.getOrDefault(to, List.of()).contains("overflow")) continue;
                            take.computeIfAbsent(o, k -> new LinkedHashMap<>()).merge(id, st.getCount(), Integer::sum);
                            put.computeIfAbsent(to, k -> new LinkedHashMap<>()).merge(id, st.getCount(), Integer::sum);
                        }
                    }
                    // The takes first (into its pack), then the puts; nearest first within each.
                    List<BlockPos> takes = new ArrayList<>(take.keySet());
                    takes.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(at)));
                    for (BlockPos p : takes) visits.add(new Visit(p, take.get(p), Map.of()));
                    List<BlockPos> puts = new ArrayList<>(put.keySet());
                    puts.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(at)));
                    for (BlockPos p : puts) visits.add(new Visit(p, Map.of(), put.get(p)));
                    if (visits.isEmpty()) return end(true, record.tidyOnly ? "The chests are tidy." : "Nothing to put away: I carry only what I work with.");
                    phase = Phase.VISIT;
                    index = 0;
                    ticks = 0;
                    return TaskState.RUNNING;
                }
                case VISIT -> {
                    if (index >= visits.size()) return end(true, line());
                    Visit v = visits.get(index);
                    if (!sp.canInteractWithBlock(v.chest(), 0.0)) {
                        if (ticks > 600) {
                            index++; // that chest can't be got to: the next
                            ticks = 0;
                            return TaskState.RUNNING;
                        }
                        if (ticks % 20 == 1) nav.navigateNear(v.chest().getX(), v.chest().getY(), v.chest().getZ(), 2);
                        nav.tick();
                        return TaskState.RUNNING;
                    }
                    nav.cancel();
                    TaskContext.inputDriver(player).clear();
                    Container c = HopperBlockEntity.getContainerAt(level, v.chest());
                    if (c != null) {
                        v.take().forEach((id, n) -> moved += takeOut(sp, c, id, n));
                        v.put().forEach((id, n) -> stored += putIn(sp, c, id, n, v.chest()));
                        c.setChanged();
                        note(sp, v.chest(), c);
                    }
                    TaskContext.syncInventory(sp);
                    index++;
                    ticks = 0;
                    return TaskState.RUNNING;
                }
                default -> {
                    return phase == Phase.DONE ? TaskState.SUCCESS : TaskState.FAILED;
                }
            }
        } catch (RuntimeException e) {
            return end(false, "something went wrong: " + e.getMessage());
        }
    }

    /**
     * The chest for this item: the nearest whose purpose names it, then one for its sort ("wool", "iron"), then one for
     * its kind ("tools"), then overflow.
     */
    private BlockPos chestFor(String id, Map<BlockPos, List<String>> purpose, List<BlockPos> overflow, BlockPos at) {
        ItemStack st = new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(id)));
        Set<String> kinds = kinds(st);
        BlockPos best = null;
        int bestRank = Integer.MAX_VALUE;
        double bestD = Double.MAX_VALUE;
        for (var e : purpose.entrySet()) {
            if (full.contains(e.getKey())) continue;
            int rank = Integer.MAX_VALUE;
            for (String w : e.getValue()) if (!"overflow".equals(w)) rank = Math.min(rank, ChestPurposes.rank(w, id, kinds));
            if (rank == Integer.MAX_VALUE) continue;
            double d = e.getKey().distSqr(at);
            if (rank < bestRank || rank == bestRank && d < bestD) {
                best = e.getKey();
                bestRank = rank;
                bestD = d;
            }
        }
        if (best != null) return best;
        for (BlockPos o : overflow) if (!full.contains(o)) return o;
        return null;
    }

    /** The kinds an item is, as a chest's purpose names them. */
    static Set<String> kinds(ItemStack st) {
        Set<String> out = new HashSet<>();
        String path = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
        if (st.isDamageableItem() || st.getItem() instanceof net.minecraft.world.item.ArmorItem) out.add("tools");
        if (st.has(net.minecraft.core.component.DataComponents.FOOD)) out.add("food");
        if (path.startsWith("raw_") || path.endsWith("_ingot") || path.endsWith("_nugget") || path.endsWith("_ore")
                || Set.of("diamond", "emerald", "lapis_lazuli", "redstone", "coal", "charcoal", "quartz", "amethyst_shard", "netherite_scrap").contains(path)) {
            out.add("ores");
        }
        if (st.is(ItemTags.LOGS) || st.is(ItemTags.PLANKS) || st.is(Items.STICK)) out.add("wood");
        if (Set.of("cobblestone", "stone", "deepslate", "cobbled_deepslate", "granite", "diorite", "andesite", "tuff", "blackstone",
                "netherrack", "calcite", "smooth_stone", "stone_bricks").contains(path)) out.add("stone");
        if (Set.of("wheat", "wheat_seeds", "beetroot_seeds", "melon_seeds", "pumpkin_seeds", "carrot", "potato", "beetroot", "sugar_cane",
                "pumpkin", "melon_slice", "cocoa_beans", "nether_wart", "sweet_berries", "glow_berries", "bamboo", "cactus").contains(path)) out.add("crops");
        if (Set.of("string", "bone", "bone_meal", "rotten_flesh", "gunpowder", "spider_eye", "ender_pearl", "slime_ball", "leather", "feather",
                "blaze_rod", "phantom_membrane", "ink_sac", "arrow").contains(path)) out.add("mob drops");
        if (st.getItem() instanceof BlockItem) out.add("blocks");
        return out;
    }

    /** What it carries and can spare: all but its best tool of each kind, 16 food and 32 torches. */
    static Map<String, Integer> spare(ServerPlayer sp) {
        var inv = sp.getInventory();
        Map<String, Integer> out = new LinkedHashMap<>();
        Map<String, Integer> keptTool = new HashMap<>(); // kind → slot of the best
        for (int i = 0; i < Math.min(36, inv.getContainerSize()); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            String kind = toolKind(st);
            if (kind == null) continue;
            Integer was = keptTool.get(kind);
            if (was == null || worth(st) > worth(inv.getItem(was))) keptTool.put(kind, i);
        }
        int food = 0, torches = 0, arrows = 0;
        boolean bow = keptTool.containsKey("bow") || keptTool.containsKey("crossbow");
        for (int i = 0; i < Math.min(36, inv.getContainerSize()); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty() || keptTool.containsValue(i)) continue;
            // Golden apples stay with it (what saves its life), and with a bow, a stack of arrows.
            if (com.mineagent.engine.survival.FoodPolicy.isGoldenApple(st.getItem())) continue;
            int n = st.getCount();
            if (bow && st.getItem() instanceof net.minecraft.world.item.ArrowItem) {
                int keep = Math.min(n, KEEP_ARROWS - arrows);
                arrows += keep;
                n -= keep;
            } else if (st.has(net.minecraft.core.component.DataComponents.FOOD) && !st.is(Items.ROTTEN_FLESH) && !st.is(Items.SPIDER_EYE)) {
                int keep = Math.min(n, KEEP_FOOD - food);
                food += keep;
                n -= keep;
            } else if (st.is(Items.TORCH)) {
                int keep = Math.min(n, KEEP_TORCHES - torches);
                torches += keep;
                n -= keep;
            }
            if (n > 0) out.merge(id(st), n, Integer::sum);
        }
        return out;
    }

    private static String toolKind(ItemStack st) {
        String path = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
        for (String k : List.of("pickaxe", "axe", "shovel", "hoe", "sword", "shears", "bow", "crossbow", "shield", "fishing_rod",
                "flint_and_steel", "water_bucket", "bucket")) {
            if (path.equals(k) || path.endsWith("_" + k)) return k;
        }
        return null;
    }

    private static int worth(ItemStack st) {
        String path = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
        int tier = path.startsWith("netherite_") ? 6 : path.startsWith("diamond_") ? 5 : path.startsWith("iron_") ? 4
                : path.startsWith("golden_") ? 2 : path.startsWith("stone_") ? 3 : 1;
        int left = st.isDamageableItem() ? st.getMaxDamage() - st.getDamageValue() : 0;
        return tier * 100_000 + (st.isEnchanted() ? 50_000 : 0) + left;
    }

    private int putIn(ServerPlayer sp, Container c, String id, int count, BlockPos chest) {
        var inv = sp.getInventory();
        int left = count, gave = 0;
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !id(s).equals(id)) continue;
            int n = Math.min(left, s.getCount());
            ItemStack moving = s.split(n);
            ItemStack rest = HopperBlockEntity.addItem(null, c, moving, null);
            if (!rest.isEmpty()) {
                s.grow(rest.getCount());
                full.add(chest); // full: what doesn't fit stays with it
            }
            if (s.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
            gave += n - rest.getCount();
            left -= n - rest.getCount();
            if (!rest.isEmpty()) break;
        }
        return gave;
    }

    private static int takeOut(ServerPlayer sp, Container c, String id, int count) {
        int left = count, got = 0;
        for (int i = 0; i < c.getContainerSize() && left > 0; i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty() || !id(s).equals(id)) continue;
            ItemStack out = c.removeItem(i, Math.min(left, s.getCount()));
            int n = out.getCount();
            if (!sp.getInventory().add(out)) {
                c.setItem(i, out); // its pack is full: left where it was
                break;
            }
            got += n;
            left -= n;
        }
        return got;
    }

    private void note(ServerPlayer sp, BlockPos at, Container c) {
        var loop = TaskContext.agentLoop(player);
        if (loop == null) return;
        Map<String, Integer> items = new HashMap<>();
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack st = c.getItem(i);
            if (!st.isEmpty()) items.merge(id(st), st.getCount(), Integer::sum);
        }
        loop.noteChest(sp.serverLevel().dimension().location().toString(), at.getX(), at.getY(), at.getZ(), items, sp.serverLevel().getGameTime());
    }

    private static String id(ItemStack st) {
        return BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
    }

    private String line() {
        if (stored == 0 && moved == 0) return "Nothing went in: the chests near are full.";
        return record.tidyOnly ? "Tidied the chests: " + moved + " things to the chests they belong in."
                : "Put away " + stored + " things by what the chests are for" + (moved > 0 ? " (and tidied " + moved + " out of the overflow)." : ".");
    }

    private TaskState end(boolean ok, String line) {
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
        outcome = line;
        var loop = TaskContext.agentLoop(player);
        if (loop != null) loop.say(ok ? line : "I couldn't put things away: " + line + ".");
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
}
