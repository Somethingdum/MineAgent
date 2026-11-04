package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.pathing.execute.PlayerNav;
import com.mineagent.engine.planning.production.GameKnowledge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Food, decided again and again from what is there now (SMH plan 70; the deciding ported from AltoClef's
 * {@code CollectFoodTask} and {@code TimeoutWanderTask}, MIT, Copyright (c) 2020 Adris Jautakas, see
 * THIRD_PARTY_NOTICES.md). It plans nothing ahead from what it remembers. Each time it chooses, it looks at the world
 * as it is: food lying on the ground near it; ripe crops; the grown animal in sight worth most for the walk (AltoClef's
 * score: the food it gives over the distance squared; the last two of a kind only when nothing else is near); ripe
 * sweet berries, the last of these, as AltoClef has them. With none of them in sight it walks to ground it hasn't been
 * on and looks again, rather than give up. At the end the raw meat it carries is cooked and any wheat baked, when it can.
 * The three-world run's third world went its whole game without food on a plan that chose mooshrooms it had seen
 * across the sea and found "none near" after: nothing here counts on what isn't in sight.
 */
public class CollectFoodTask extends CompanionTask<CollectFoodTask.FoodRecord> {
    public static final class FoodRecord extends TaskRecord {
        /** How many more pieces of food it should have on it at the end. */
        public final int count;
        /** Only what is in sight now (food for a trip, got before it sets out): no walks to new ground. */
        public final boolean onlyInSight;

        public FoodRecord(String toolCallId, int count) {
            this(toolCallId, count, false);
        }

        public FoodRecord(String toolCallId, int count, boolean onlyInSight) {
            super(toolCallId);
            this.count = Math.max(1, count);
            this.onlyInSight = onlyInSight;
        }
    }

    enum Doing { DECIDE, PICKUP, HARVEST, BERRIES, KILL, LOOT, WANDER, COOK, DONE }

    /** The animals it eats, by what their cooked meat feeds (AltoClef's order of preference, mooshrooms with cows). */
    static final Map<String, Integer> MEAT = new LinkedHashMap<>();
    static {
        MEAT.put("minecraft:cow", 8);
        MEAT.put("minecraft:mooshroom", 8);
        MEAT.put("minecraft:pig", 8);
        MEAT.put("minecraft:chicken", 6);
        MEAT.put("minecraft:sheep", 6);
        MEAT.put("minecraft:rabbit", 5);
    }
    /** Raw food it cooks at the end, and what it becomes. */
    static final Map<Item, Item> COOKS = new LinkedHashMap<>();
    static {
        COOKS.put(Items.BEEF, Items.COOKED_BEEF);
        COOKS.put(Items.PORKCHOP, Items.COOKED_PORKCHOP);
        COOKS.put(Items.CHICKEN, Items.COOKED_CHICKEN);
        COOKS.put(Items.MUTTON, Items.COOKED_MUTTON);
        COOKS.put(Items.RABBIT, Items.COOKED_RABBIT);
        COOKS.put(Items.POTATO, Items.BAKED_POTATO);
    }

    /** How far it looks for each, in blocks. */
    static final int DROPS = 16, CROPS = 16, ANIMALS = 32, BERRIES = 32;
    /** One walk to new ground: how far, how long at most, and how many before it stops. */
    static final int WANDER = 40, WANDER_TICKS = 600, WANDERS = 12;
    /** It goes no further than this from where it set out. */
    static final int ROAM = 200;

    private PlayerNav nav;
    private volatile boolean arrived, navFailed;
    private Doing doing = Doing.DECIDE;
    private BlockPos startAt, goal, lootAt;
    private Entity prey;
    private ItemEntity drop;
    private int startFood = -1, ticks, wanders, cooks;
    private double closest;
    private int still;
    private final Set<UUID> unreachable = new HashSet<>();
    private final Set<BlockPos> badBlocks = new HashSet<>();
    private final List<BlockPos> looked = new ArrayList<>();
    private ProductionTask sub;
    private String outcome = "";

    public CollectFoodTask(AgentPlayer player, FoodRecord record) {
        super(player, record);
    }

    private ServerPlayer sp() {
        return TaskContext.serverPlayer(player);
    }

    private static boolean trace() {
        return Boolean.getBoolean("mineagent.traceWork");
    }

    @Override
    protected void onStart() {
        ServerPlayer sp = sp();
        if (nav == null) {
            nav = new PlayerNav(player, TaskContext.navCaches(player));
            nav.setListener(new PlayerNav.NavListener() {
                @Override public void onGoalReached() { arrived = true; }
                @Override public void onNavigationFailed(String reason) { navFailed = true; }
            });
        }
        if (startFood < 0) {
            startFood = food(sp);
            startAt = sp.blockPosition().immutable();
            record.extendDeadlineTo(Math.max(record.deadline(), sp.level().getGameTime() + 12_000L));
        }
        if (sub != null) sub.resume();
        goal = null;
    }

    /** The food it carries, in pieces (none of what poisons or starves). */
    static int food(ServerPlayer sp) {
        int n = 0;
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (edible(inv.getItem(i))) n += inv.getItem(i).getCount();
        return n;
    }

    static boolean edible(ItemStack st) {
        return !st.isEmpty() && st.get(DataComponents.FOOD) != null && !st.is(Items.ROTTEN_FLESH) && !st.is(Items.SPIDER_EYE)
                && !st.is(Items.POISONOUS_POTATO) && !st.is(Items.PUFFERFISH);
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = sp();
        ticks++;
        return switch (doing) {
            case DECIDE -> decide(sp);
            case PICKUP -> pickup(sp);
            case HARVEST -> harvest(sp);
            case BERRIES -> berries(sp);
            case KILL -> kill(sp);
            case LOOT -> loot(sp);
            case WANDER -> wander(sp);
            case COOK -> cook(sp);
            case DONE -> TaskState.SUCCESS;
        };
    }

    private int gained(ServerPlayer sp) {
        return food(sp) - startFood;
    }

    /** What it does next, from what it sees now. */
    private TaskState decide(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        String dim = level.dimension().location().toString();
        nav.cancel();
        if (gained(sp) >= record.count) return startCooking(sp);
        BlockPos at = sp.blockPosition();
        // Food on the ground.
        drop = null;
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(DROPS, 6, DROPS),
                e -> e.isAlive() && edible(e.getItem()) && com.mineagent.engine.survival.Manners.mayTake(e, sp) && !unreachable.contains(e.getUUID()))) {
            if (drop == null || e.distanceToSqr(sp) < drop.distanceToSqr(sp)) drop = e;
        }
        if (drop != null) return go(Doing.PICKUP, drop.blockPosition(), "food on the ground");
        // Ripe crops (wheat for bread), then berries last, as AltoClef has it: the crops first.
        BlockPos crop = nearest(sp, CROPS, 4, (p, st) -> st.getBlock() instanceof CropBlock c && c.isMaxAge(st)
                && (st.is(Blocks.WHEAT) || st.is(Blocks.CARROTS) || st.is(Blocks.POTATOES) || st.is(Blocks.BEETROOTS)));
        if (crop != null) return go(Doing.HARVEST, crop, "ripe crops");
        // The animal worth most for the walk.
        Entity best = null;
        double bestScore = 0;
        Map<String, Integer> herd = new LinkedHashMap<>();
        List<Entity> seen = level.getEntities((Entity) null, new AABB(at).inflate(ANIMALS, 16, ANIMALS),
                e -> GameKnowledge.huntable(e) && MEAT.containsKey(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString())
                        && !unreachable.contains(e.getUUID()) && !(e instanceof LivingEntity l && l.isBaby())
                        && !com.mineagent.engine.world.AfkFarms.protects(dim, e.getBlockX(), e.getBlockY(), e.getBlockZ()));
        for (Entity e : seen) herd.merge(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString(), 1, Integer::sum);
        boolean others = herd.size() > 1 || herd.values().stream().anyMatch(n -> n > 2);
        for (Entity e : seen) {
            String kind = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
            double score = 100.0 * MEAT.get(kind) / Math.max(1.0, e.distanceToSqr(sp));
            // The last two of a kind, kept to breed while there are others to take.
            if (herd.get(kind) <= GameKnowledge.LEAVE && others) score *= 0.1;
            if (!sp.hasLineOfSight(e)) score *= 0.5;
            if (score > bestScore) {
                bestScore = score;
                best = e;
            }
        }
        if (best != null) {
            prey = best;
            closest = Double.MAX_VALUE;
            still = 0;
            return go(Doing.KILL, best.blockPosition(), "a " + BuiltInRegistries.ENTITY_TYPE.getKey(best.getType()).getPath());
        }
        BlockPos bush = nearest(sp, BERRIES, 6, (p, st) -> ProductionTask.ripeBerries(st) != null);
        if (bush != null) return go(Doing.BERRIES, bush, "ripe berries");
        // None of it in sight: new ground, and look again (AltoClef's wander); for a trip's food, what it has.
        looked.add(at);
        if (record.onlyInSight) {
            outcome = "none in sight here";
            return gained(sp) > 0 ? startCooking(sp) : TaskState.FAILED;
        }
        if (++wanders > WANDERS) {
            outcome = "no food to be had within " + (int) Math.sqrt(startAt.distSqr(at)) + " blocks of where I set out, after "
                    + WANDERS + " walks looking";
            return gained(sp) > 0 ? startCooking(sp) : TaskState.FAILED;
        }
        BlockPos to = newGround(sp);
        if (trace()) System.out.println("[MineAgent] food: nothing to eat in sight at " + at.toShortString() + "; looking at new ground at "
                + to.toShortString() + " (walk " + wanders + " of " + WANDERS + ")");
        return go(Doing.WANDER, to, null);
    }

    private TaskState go(Doing what, BlockPos to, String why) {
        if (trace() && why != null) System.out.println("[MineAgent] food: " + why + " at " + to.toShortString() + " from "
                + sp().blockPosition().toShortString() + "; " + gained(sp()) + " of " + record.count);
        doing = what;
        goal = to.immutable();
        ticks = 0;
        still = 0;
        arrived = navFailed = false;
        nav.navigateTo(to.getX(), to.getY(), to.getZ());
        return TaskState.RUNNING;
    }

    /** The nearest block within reach of a look that {@code ok} takes, not one it gave up on. */
    private BlockPos nearest(ServerPlayer sp, int across, int up, java.util.function.BiPredicate<BlockPos, BlockState> ok) {
        ServerLevel level = sp.serverLevel();
        String dim = level.dimension().location().toString();
        BlockPos at = sp.blockPosition(), best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-across, -up, -across), at.offset(across, up, across))) {
            if (!level.isLoaded(p) || badBlocks.contains(p)) continue;
            BlockState st = level.getBlockState(p);
            if (!ok.test(p, st) || com.mineagent.engine.world.AfkFarms.protects(dim, p.getX(), p.getY(), p.getZ())) continue;
            double d = p.distSqr(at);
            if (d < bestDistance) {
                bestDistance = d;
                best = p.immutable();
            }
        }
        return best;
    }

    /**
     * Ground it hasn't looked over: of eight ways out, a walk's length off, the one furthest from everywhere it has
     * looked so far, on the ground there, never more than its roam from where it set out.
     */
    private BlockPos newGround(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        BlockPos at = sp.blockPosition(), best = null;
        double bestScore = -1;
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8 + (wanders * 0.7);
            int x = at.getX() + (int) Math.round(Math.cos(a) * WANDER), z = at.getZ() + (int) Math.round(Math.sin(a) * WANDER);
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            BlockPos p = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (p.distSqr(startAt) > (double) ROAM * ROAM) continue;
            if (!level.getFluidState(p).isEmpty() || !level.getFluidState(p.below()).isEmpty()) continue; // no wandering out to sea
            double nearestLooked = Double.MAX_VALUE;
            for (BlockPos l : looked) nearestLooked = Math.min(nearestLooked, l.distSqr(p));
            if (nearestLooked > bestScore) {
                bestScore = nearestLooked;
                best = p;
            }
        }
        if (best == null) {
            // Every way out is sea, unloaded or past its roam: back toward where it set out.
            best = startAt;
        }
        return best;
    }

    /** Walking on; true while the walk is still going and has time left. */
    private boolean walking(ServerPlayer sp, int limit, double near) {
        nav.tick();
        if (goal != null && sp.blockPosition().distSqr(goal) <= near * near) return false;
        return !arrived && !navFailed && ticks < limit;
    }

    private TaskState pickup(ServerPlayer sp) {
        if (drop == null || !drop.isAlive()) {
            doing = Doing.DECIDE;
            return TaskState.RUNNING;
        }
        if (walking(sp, 300, 0.5)) return TaskState.RUNNING;
        if (drop.isAlive() && drop.distanceToSqr(sp) > 2.0) unreachable.add(drop.getUUID());
        doing = Doing.DECIDE;
        return TaskState.RUNNING;
    }

    private TaskState harvest(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        BlockState st = level.getBlockState(goal);
        if (!(st.getBlock() instanceof CropBlock c && c.isMaxAge(st))) {
            lootAt = goal;
            ticks = 0;
            doing = Doing.LOOT;
            return TaskState.RUNNING;
        }
        if (goal.distToCenterSqr(sp.getEyePosition()) > 4.0 * 4.0) {
            if (walking(sp, 600, 1.0)) return TaskState.RUNNING;
            if (goal.distToCenterSqr(sp.getEyePosition()) > 4.5 * 4.5) {
                badBlocks.add(goal);
                doing = Doing.DECIDE;
                return TaskState.RUNNING;
            }
        }
        nav.cancel();
        // Started once and left to run (a fresh start sets a break back to nothing).
        if (!BlockDigger.breaking(sp, goal) && !BlockDigger.startBreakingDetailed(sp, goal).allowed() && ++still > 40) {
            badBlocks.add(goal);
            doing = Doing.DECIDE;
        }
        return TaskState.RUNNING;
    }

    private TaskState berries(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        if (ProductionTask.ripeBerries(level.getBlockState(goal)) == null) {
            lootAt = goal;
            ticks = 0;
            doing = Doing.LOOT;
            return TaskState.RUNNING;
        }
        if (goal.distToCenterSqr(sp.getEyePosition()) > 4.0 * 4.0) {
            if (walking(sp, 600, 1.0)) return TaskState.RUNNING;
            if (goal.distToCenterSqr(sp.getEyePosition()) > 4.5 * 4.5) {
                badBlocks.add(goal);
                doing = Doing.DECIDE;
                return TaskState.RUNNING;
            }
        }
        nav.cancel();
        // Picked as a player picks them: used by hand (not with bone meal in it, which grows them), the bush left.
        if (sp.getMainHandItem().is(Items.BONE_MEAL)) sp.getInventory().selected = (sp.getInventory().selected + 1) % 9;
        sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, net.minecraft.world.phys.Vec3.atCenterOf(goal));
        com.mineagent.engine.act.Interaction.interactBlock(sp, goal, InteractionHand.MAIN_HAND);
        TaskContext.syncInventory(sp);
        if (++still > 20) {
            badBlocks.add(goal); // it won't give them up from here
            doing = Doing.DECIDE;
        }
        return TaskState.RUNNING;
    }

    private TaskState kill(ServerPlayer sp) {
        if (prey == null || prey.isRemoved()) {
            doing = Doing.DECIDE;
            return TaskState.RUNNING;
        }
        if (!prey.isAlive()) {
            lootAt = prey.blockPosition().immutable();
            prey = null;
            ticks = 0;
            doing = Doing.LOOT;
            TaskContext.inputDriver(player).clear();
            return TaskState.RUNNING;
        }
        if (!com.mineagent.engine.act.Interaction.inEntityReach(sp, prey) || !sp.hasLineOfSight(prey)) {
            // After it: the way made again when it has moved off or every six seconds, never while a search is under way
            // (a search begun again never ends, and a new goal starts its walk rules over).
            nav.tick();
            boolean searching = nav.core().state() == com.mineagent.engine.pathing.execute.PathingCore.State.SEARCH;
            if (goal == null || navFailed || !searching && (goal.distManhattan(prey.blockPosition()) >= 3 || ticks % 120 == 0)) {
                goal = prey.blockPosition().immutable();
                navFailed = arrived = false;
                nav.navigateNear(goal.getX(), goal.getY(), goal.getZ(), 1);
            }
            // Ten seconds without getting a block nearer (behind a fence, across water, up where it can't go): the next.
            double d = sp.distanceTo(prey);
            if (d < closest - 1.0) {
                closest = d;
                still = 0;
            } else if (++still > 200) {
                unreachable.add(prey.getUUID());
                prey = null;
                doing = Doing.DECIDE;
            }
            return TaskState.RUNNING;
        }
        nav.cancel();
        TaskContext.inputDriver(player).clear();
        com.mineagent.engine.survival.MobDefenseChain.equipWeapon(sp);
        var to = prey.position().add(0, prey.getBbHeight() * 0.5, 0).subtract(sp.getEyePosition());
        sp.setYRot((float) Math.toDegrees(Math.atan2(-to.x, to.z)));
        sp.setXRot((float) -Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z))));
        sp.setYHeadRot(sp.getYRot());
        if (sp.getAttackStrengthScale(0.5f) >= 0.9f) com.mineagent.engine.act.Interaction.attackEntity(sp, prey);
        return TaskState.RUNNING;
    }

    /** What a kill or a harvest dropped, picked up: a few seconds' walk over them. */
    private TaskState loot(ServerPlayer sp) {
        if (lootAt == null || ticks > 120) {
            lootAt = null;
            still = 0;
            doing = Doing.DECIDE;
            return TaskState.RUNNING;
        }
        if (ticks < 12) return TaskState.RUNNING; // drops can be picked up after ten ticks
        ItemEntity near = null;
        for (ItemEntity e : sp.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(lootAt).inflate(5, 3, 5),
                e -> e.isAlive() && com.mineagent.engine.survival.Manners.mayTake(e, sp))) {
            if (near == null || e.distanceToSqr(sp) < near.distanceToSqr(sp)) near = e;
        }
        if (near == null) {
            lootAt = null;
            still = 0;
            doing = Doing.DECIDE;
            return TaskState.RUNNING;
        }
        nav.tick();
        if (goal == null || !goal.equals(near.blockPosition()) || navFailed) {
            goal = near.blockPosition().immutable();
            navFailed = arrived = false;
            nav.navigateTo(goal.getX(), goal.getY(), goal.getZ());
        }
        return TaskState.RUNNING;
    }

    private TaskState wander(ServerPlayer sp) {
        // While it walks, a look round every two seconds: food come into sight is gone for at once (AltoClef re-decides
        // each tick; every forty is enough here).
        if (ticks % 40 == 39 && something(sp)) {
            doing = Doing.DECIDE;
            return TaskState.RUNNING;
        }
        if (walking(sp, WANDER_TICKS, 3.0)) return TaskState.RUNNING;
        doing = Doing.DECIDE;
        return TaskState.RUNNING;
    }

    /** Food in sight now, of any kind it would go for. */
    private boolean something(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        BlockPos at = sp.blockPosition();
        if (!level.getEntities((Entity) null, new AABB(at).inflate(ANIMALS, 16, ANIMALS),
                e -> GameKnowledge.huntable(e) && MEAT.containsKey(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString())
                        && !unreachable.contains(e.getUUID()) && !(e instanceof LivingEntity l && l.isBaby())).isEmpty()) return true;
        if (!level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(DROPS, 6, DROPS), e -> e.isAlive() && edible(e.getItem())).isEmpty()) return true;
        return nearest(sp, 12, 4, (p, st) -> ProductionTask.ripeBerries(st) != null
                || st.getBlock() instanceof CropBlock c && c.isMaxAge(st)) != null;
    }

    /** Raw meat cooked and wheat baked, when it can (a furnace, or the stone for one; a table for bread). */
    private TaskState startCooking(ServerPlayer sp) {
        nav.cancel();
        doing = Doing.COOK;
        return TaskState.RUNNING;
    }

    private TaskState cook(ServerPlayer sp) {
        if (sub != null) {
            TaskState st = sub.tick();
            if (st == TaskState.RUNNING) return TaskState.RUNNING;
            sub = null;
        }
        if (cooks < COOKS.size() + 1) {
            int i = 0;
            for (var e : COOKS.entrySet()) {
                if (i++ < cooks) continue;
                cooks++;
                int raw = sp.getInventory().countItem(e.getKey());
                if (raw <= 0) continue;
                sub = new ProductionTask(player, new ProductionTask.ProduceRecord(record.toolCallId() + ":cook" + cooks,
                        BuiltInRegistries.ITEM.getKey(e.getValue()).toString(), raw, null, false, true));
                sub.start();
                return TaskState.RUNNING;
            }
            cooks = COOKS.size() + 1;
            int wheat = sp.getInventory().countItem(Items.WHEAT);
            if (wheat >= 3) {
                sub = new ProductionTask(player, new ProductionTask.ProduceRecord(record.toolCallId() + ":bread", "minecraft:bread", wheat / 3,
                        null, false, true));
                sub.start();
                return TaskState.RUNNING;
            }
        }
        int got = gained(sp);
        outcome = got >= record.count ? "" : outcome;
        doing = Doing.DONE;
        return got > 0 ? TaskState.SUCCESS : TaskState.FAILED;
    }

    /** Why it got less than it was asked for, or nothing ("" when it got them all). */
    public String outcome() {
        return outcome;
    }

    /** How many pieces of food it has got so far. */
    public int got() {
        return Math.max(0, gained(sp()));
    }

    @Override
    protected String successMessage() {
        int got = Math.max(0, gained(sp()));
        return "{\"success\":true,\"note\":\"" + got + " food got" + (outcome.isEmpty() ? "" : ", as much as I could: " + outcome) + "\"}";
    }

    @Override
    protected String failureMessage() {
        return "{\"success\":false,\"note\":\"" + (outcome.isEmpty() ? "no food got" : outcome) + "\"}";
    }

    @Override
    protected void onInterrupt() {
        if (nav != null) nav.cancel();
        if (sub != null) sub.interrupt();
        TaskContext.inputDriver(player).clear();
    }
}
