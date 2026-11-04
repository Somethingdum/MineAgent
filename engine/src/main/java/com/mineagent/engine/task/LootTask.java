package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.pathing.execute.PlayerNav;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loot (SMH plan 70: "collect the craziest loot"): the chests and barrels the world made and no one has opened yet (they
 * still hold their loot table; a chest someone filled never does, so its owner's chests are never touched), round where
 * it stands, or round the nearest ruined portal, village, igloo or beached shipwreck it hasn't looted (sent with "go
 * looting"), desert and jungle temples and mineshafts among them. Each opened as a player opens one and everything taken,
 * and the chest minecarts the world made (a mineshaft's) the same way. Traps are taken apart first, from beside, never
 * stepped on (SMH plan 70, its owner: "Temple traps should be programmed"): a jungle temple's tripwire hooks broken before
 * their string (string cut with shears, or left once its hooks are gone), its dispensers broken (their arrows taken); a
 * desert temple's pressure plate over the TNT broken from the floor beside it (the pathfinder never steps on a plate with
 * TNT under it, nor on string). A trapped chest (redstone of its own) and any chest by a trap it couldn't take apart is
 * left alone and said so.
 */
public class LootTask extends CompanionTask<LootTask.LootRecord> {
    public static final class LootRecord extends TaskRecord {
        /** Go to the nearest structure worth looting first (else: round where it stands). */
        public final boolean go;
        /** The structures to go to (null: {@link #SITES}); a bastion for a smithing template, say. */
        public final List<String> sites;

        public LootRecord(String toolCallId, boolean go) {
            this(toolCallId, go, null);
        }

        public LootRecord(String toolCallId, boolean go, List<String> sites) {
            super(toolCallId);
            this.go = go;
            this.sites = sites == null ? null : List.copyOf(sites);
        }
    }

    /** How far round it looks for chests. */
    static final int RADIUS = 48;
    /** The structures it goes looting at: on the surface, no boss, no warden, no traps it can't see. */
    static final List<String> SITES = List.of("minecraft:ruined_portal", "minecraft:ruined_portal_desert", "minecraft:ruined_portal_jungle",
            "minecraft:ruined_portal_swamp", "minecraft:ruined_portal_mountain", "minecraft:village_plains", "minecraft:village_desert",
            "minecraft:village_savanna", "minecraft:village_snowy", "minecraft:village_taiga", "minecraft:igloo", "minecraft:shipwreck_beached",
            "minecraft:desert_pyramid", "minecraft:jungle_pyramid", "minecraft:mineshaft", "minecraft:mineshaft_mesa");
    /** Sites under the ground: gone to at their own depth, not the surface over them. */
    static final java.util.Set<String> UNDERGROUND = java.util.Set.of("minecraft:mineshaft", "minecraft:mineshaft_mesa");

    private enum Phase { FIND, TRAVEL, SCAN, DISARM, OPEN, CARTS, DONE }

    private PlayerNav nav;
    private Phase phase = Phase.FIND;
    private BlockPos site, triedAt;
    private final List<BlockPos> chests = new ArrayList<>();
    /** The parts of the traps by its chests, in the order to take them apart; those it couldn't. */
    private final List<BlockPos> parts = new ArrayList<>();
    private final List<BlockPos> stuckParts = new ArrayList<>();
    /** Chest minecarts with their loot still in them. */
    private final List<net.minecraft.world.entity.vehicle.MinecartChest> carts = new ArrayList<>();
    private boolean underground;
    private int part, disarmed;
    private int index, ticks, opened, skipped, openTries;
    private final Map<String, Integer> got = new LinkedHashMap<>();
    private String outcome = "";

    public LootTask(AgentPlayer player, LootRecord record) {
        super(player, record);
    }

    @Override
    protected void onStart() {
        if (nav == null) nav = new PlayerNav(player, TaskContext.navCaches(player));
        record.extendDeadlineTo(Math.max(record.deadline(), TaskContext.serverPlayer(player).level().getGameTime() + 48_000L));
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        ServerLevel level = sp.serverLevel();
        try {
            ticks++;
            switch (phase) {
                case FIND -> {
                    if (!record.go) {
                        site = sp.blockPosition();
                        phase = Phase.SCAN;
                        return TaskState.RUNNING;
                    }
                    site = nearestSite(sp, level);
                    if (site == null) return end(false, "there's nowhere worth looting within 1,600 blocks I haven't looted");
                    say("Off looting: the nearest place worth it, at " + site.getX() + ", " + site.getZ() + ", "
                            + (int) Math.sqrt(site.distSqr(sp.blockPosition())) + " blocks away.");
                    phase = Phase.TRAVEL;
                    ticks = 0;
                    return TaskState.RUNNING;
                }
                case TRAVEL -> {
                    double dx = sp.getX() - site.getX(), dz = sp.getZ() - site.getZ();
                    if (dx * dx + dz * dz <= 24.0 * 24.0 && (!underground || Math.abs(sp.getY() - site.getY()) <= 12)) {
                        nav.cancel();
                        phase = Phase.SCAN;
                        return TaskState.RUNNING;
                    }
                    if (ticks > 36_000) return end(false, "I couldn't get there");
                    if (ticks % 100 == 1 || !nav.isNavigating()) {
                        int y = underground ? site.getY() : level.isLoaded(site) ? level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                site.getX(), site.getZ()) : sp.getBlockY();
                        nav.navigateNear(site.getX(), y, site.getZ(), 8);
                    }
                    nav.tick();
                    return TaskState.RUNNING;
                }
                case SCAN -> {
                    chests.clear();
                    parts.clear();
                    for (BlockPos p : unopened(level, site, RADIUS)) {
                        if (level.getBlockEntity(p) instanceof TrappedChestBlockEntity) {
                            skipped++;
                            continue;
                        }
                        chests.add(p);
                        for (BlockPos q : trapParts(level, p)) if (!parts.contains(q)) parts.add(q);
                    }
                    chests.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(sp.blockPosition())));
                    order(parts, level);
                    carts.clear();
                    carts.addAll(level.getEntitiesOfClass(net.minecraft.world.entity.vehicle.MinecartChest.class,
                            new net.minecraft.world.phys.AABB(site).inflate(RADIUS, 24, RADIUS), c -> c.isAlive() && c.getLootTable() != null));
                    carts.sort(java.util.Comparator.comparingDouble(c -> c.distanceToSqr(sp)));
                    System.out.println("[MineAgent] loot: " + chests.size() + " unopened, " + parts.size() + " trap parts, " + carts.size()
                            + " chest minecarts, " + skipped + " trapped, round " + site.toShortString());
                    var loop = TaskContext.agentLoop(player);
                    if (loop != null && record.go) loop.noteLooted(level.dimension().location().toString(), site);
                    if (chests.isEmpty() && carts.isEmpty()) return end(opened > 0 || skipped == 0 && !record.go,
                            skipped > 0 ? "the only chests here are trapped" : "there are no unopened chests here");
                    phase = parts.isEmpty() ? Phase.OPEN : Phase.DISARM;
                    if (!parts.isEmpty()) say("Taking the traps apart first: " + parts.size() + (parts.size() == 1 ? " part." : " parts."));
                    index = 0;
                    part = 0;
                    ticks = 0;
                    return TaskState.RUNNING;
                }
                case DISARM -> {
                    // Gone (broken by it, or never there): the next part.
                    while (part < parts.size() && !isTrapPart(level, parts.get(part))) {
                        if (gone.add(parts.get(part))) disarmed++; // a part tried twice is counted once
                        part++;
                        ticks = 0;
                    }
                    if (part >= parts.size()) {
                        // Chests by a part it couldn't take apart stay shut.
                        if (!stuckParts.isEmpty()) {
                            int before = chests.size();
                            chests.removeIf(c -> stuckParts.stream().anyMatch(q -> q.distSqr(c) <= 6 * 6));
                            skipped += before - chests.size();
                        }
                        if (disarmed > 0) say("Traps taken apart: " + disarmed + (disarmed == 1 ? " part." : " parts."));
                        phase = Phase.OPEN;
                        index = 0;
                        ticks = 0;
                        return TaskState.RUNNING;
                    }
                    BlockPos q = parts.get(part);
                    // From beside it, never on it: the pathfinder keeps off a plate over TNT and off string.
                    if (q.distToCenterSqr(sp.getEyePosition()) > 4.0 * 4.0 || standingOn(sp, q)) {
                        if (Boolean.getBoolean("mineagent.traceWork") && ticks % 100 == 1) {
                            System.out.println("[MineAgent] loot: walking to " + level.getBlockState(q).getBlock() + " at " + q.toShortString() + " from "
                                    + sp.blockPosition().toShortString() + ", navigating " + nav.isNavigating());
                        }
                        if (ticks > 900) {
                            later(q);
                            return TaskState.RUNNING;
                        }
                        if (ticks % 40 == 1 || !nav.isNavigating()) nav.navigateNear(q.getX(), q.getY(), q.getZ(), 2);
                        nav.tick();
                        return TaskState.RUNNING;
                    }
                    nav.cancel();
                    TaskContext.inputDriver(player).clear();
                    var st = level.getBlockState(q);
                    // String cut with shears doesn't set it off (with its hooks gone first, nothing does).
                    if (st.is(Blocks.TRIPWIRE)) TaskContext.selectInventoryItem(player, "minecraft:shears");
                    if (ticks % 20 == 1) {
                        var a = BlockDigger.startBreakingDetailed(sp, q);
                        if (Boolean.getBoolean("mineagent.traceWork") && ticks % 100 == 1) {
                            System.out.println("[MineAgent] loot: taking apart " + st.getBlock() + " at " + q.toShortString() + " from "
                                    + sp.blockPosition().toShortString() + ", " + String.format("%.1f", Math.sqrt(q.distToCenterSqr(sp.getEyePosition())))
                                    + " off: " + a.status() + " " + a.detail());
                        }
                    }
                    if (ticks > 400) later(q);
                    return TaskState.RUNNING;
                }
                case CARTS -> {
                    while (index < carts.size() && (!carts.get(index).isAlive() || carts.get(index).getLootTable() == null && carts.get(index) != triedCart)) index++;
                    if (index >= carts.size()) return end(true, null);
                    if (full(sp)) return end(opened > 0, "my pack is full");
                    var cart = carts.get(index);
                    if (sp.containerMenu != sp.inventoryMenu) {
                        takeAll(sp, sp.containerMenu);
                        sp.closeContainer();
                        opened++;
                        index++;
                        ticks = 0;
                        return TaskState.RUNNING;
                    }
                    if (!com.mineagent.engine.act.Interaction.inEntityReach(sp, cart)) {
                        if (ticks > 900) {
                            index++;
                            ticks = 0;
                            return TaskState.RUNNING;
                        }
                        if (ticks % 40 == 1 || !nav.isNavigating()) nav.navigateNear(cart.getBlockX(), cart.getBlockY(), cart.getBlockZ(), 1);
                        nav.tick();
                        return TaskState.RUNNING;
                    }
                    nav.cancel();
                    TaskContext.inputDriver(player).clear();
                    triedCart = cart;
                    sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, cart.position());
                    com.mineagent.engine.act.Interaction.interactEntity(sp, cart, net.minecraft.world.InteractionHand.MAIN_HAND);
                    if (sp.containerMenu != sp.inventoryMenu) {
                        takeAll(sp, sp.containerMenu);
                        sp.closeContainer();
                        opened++;
                        index++;
                        ticks = 0;
                        return TaskState.RUNNING;
                    }
                    if (++openTries > 30) {
                        index++;
                        openTries = 0;
                        ticks = 0;
                    }
                    return TaskState.RUNNING;
                }
                case OPEN -> {
                    if (index >= chests.size()) {
                        System.out.println("[MineAgent] loot: done with " + chests.size() + ", opened " + opened);
                        if (!carts.isEmpty()) {
                            phase = Phase.CARTS;
                            index = 0;
                            ticks = 0;
                            openTries = 0;
                            return TaskState.RUNNING;
                        }
                        return end(true, null);
                    }
                    if (full(sp)) return end(opened > 0, "my pack is full");
                    BlockPos c = chests.get(index);
                    if (sp.containerMenu != sp.inventoryMenu) {
                        // Open (its loot was made as it opened): all of it into the pack.
                        takeAll(sp, sp.containerMenu);
                        sp.closeContainer();
                        opened++;
                        index++;
                        ticks = 0;
                        openTries = 0;
                        return TaskState.RUNNING;
                    }
                    if (!(level.getBlockEntity(c) instanceof RandomizableContainerBlockEntity rc)) {
                        index++; // gone
                        ticks = 0;
                        return TaskState.RUNNING;
                    }
                    if (rc.getLootTable() == null) {
                        System.out.println("[MineAgent] loot: " + c.toShortString() + " already opened (tried " + c.equals(triedAt) + ")");
                        // Opened by it (its loot made as it opened, the screen shut again at once): taken from the chest
                        // itself. Else someone else opened it since.
                        if (c.equals(triedAt)) {
                            takeFrom(sp, rc);
                            opened++;
                        }
                        index++;
                        ticks = 0;
                        return TaskState.RUNNING;
                    }
                    // A bastion's chest with piglins near: every one that sees it open turns on it, so they are dealt with
                    // first, when its armour is up to it; else the chest is left.
                    if (com.mineagent.engine.survival.Bastions.angers(level, c, sp)) {
                        var piglin = nearestPiglin(level, c);
                        if (piglin != null) {
                            if (sp.getArmorValue() < 15 || ++clearTicks > 2_400) {
                                if (sp.getArmorValue() < 15) say("Piglins guard the chest at " + c.toShortString() + " and my armour isn't up to them: I'm leaving it.");
                                skipped++;
                                index++;
                                ticks = 0;
                                clearTicks = 0;
                                return TaskState.RUNNING;
                            }
                            if (!com.mineagent.engine.act.Interaction.inEntityReach(sp, piglin)) {
                                if (ticks % 20 == 1 || !nav.isNavigating()) nav.navigateNear(piglin.getBlockX(), piglin.getBlockY(), piglin.getBlockZ(), 1);
                                nav.tick();
                                return TaskState.RUNNING;
                            }
                            nav.cancel();
                            com.mineagent.engine.survival.MobDefenseChain.equipWeapon(sp);
                            sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, piglin.getEyePosition());
                            if (sp.getAttackStrengthScale(0.5f) >= 0.9f) com.mineagent.engine.act.Interaction.attackEntity(sp, piglin);
                            return TaskState.RUNNING;
                        }
                    }
                    clearTicks = 0;
                    // Near enough by the chest's middle (as the opening measures it), with a step to spare.
                    // A few tries that didn't open it (something between): right up to it.
                    double reach = openTries > 8 ? 1.8 : 3.5;
                    if (c.distToCenterSqr(sp.getEyePosition()) > reach * reach) {
                        if (ticks > 900) { // no way to it
                            index++;
                            ticks = 0;
                            openTries = 0;
                            return TaskState.RUNNING;
                        }
                        if (ticks % 40 == 1 || !nav.isNavigating()) nav.navigateNear(c.getX(), c.getY(), c.getZ(), openTries > 8 ? 1 : 2);
                        nav.tick();
                        return TaskState.RUNNING;
                    }
                    nav.cancel();
                    TaskContext.inputDriver(player).clear();
                    triedAt = c;
                    boolean open = com.mineagent.engine.act.Interaction.openContainer(sp, c);
                    if (sp.containerMenu != sp.inventoryMenu) { // open: all of it into the pack, as it stands open
                        takeAll(sp, sp.containerMenu);
                        sp.closeContainer();
                        opened++;
                        index++;
                        ticks = 0;
                        openTries = 0;
                        return TaskState.RUNNING;
                    }
                    if (!open && ++openTries > 30) {
                        index++;
                        ticks = 0;
                        openTries = 0;
                    }
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

    /** Everything in an open container's slots, shift-clicked into its pack. */
    private void takeAll(ServerPlayer sp, AbstractContainerMenu menu) {
        int slots = menu.slots.size() - 36; // the container's own slots come before the pack's
        for (int i = 0; i < slots; i++) {
            ItemStack st = menu.getSlot(i).getItem();
            if (st.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
            int n = st.getCount();
            menu.quickMoveStack(sp, i);
            int left = menu.getSlot(i).getItem().getCount();
            if (n - left > 0) got.merge(id, n - left, Integer::sum);
        }
        TaskContext.syncInventory(sp);
    }

    /** Everything in a chest it opened, into its pack, as far as it fits. */
    private void takeFrom(ServerPlayer sp, RandomizableContainerBlockEntity chest) {
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack st = chest.getItem(i);
            if (st.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
            ItemStack moving = st.copy();
            sp.getInventory().add(moving); // what doesn't fit stays in the copy
            int taken = st.getCount() - moving.getCount();
            if (taken <= 0) continue;
            chest.removeItem(i, taken);
            got.merge(id, taken, Integer::sum);
        }
        chest.setChanged();
        TaskContext.syncInventory(sp);
    }

    private static boolean full(ServerPlayer sp) {
        return sp.getInventory().getFreeSlot() < 0;
    }

    /** Chests and barrels within {@code r} of {@code at} that still hold their loot table: made by the world, never opened. */
    static List<BlockPos> unopened(ServerLevel level, BlockPos at, int r) {
        List<BlockPos> out = new ArrayList<>();
        int c0x = (at.getX() - r) >> 4, c1x = (at.getX() + r) >> 4, c0z = (at.getZ() - r) >> 4, c1z = (at.getZ() + r) >> 4;
        for (int cx = c0x; cx <= c1x; cx++) {
            for (int cz = c0z; cz <= c1z; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                for (var be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (!(be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) || be instanceof TrappedChestBlockEntity) continue;
                    if (((RandomizableContainerBlockEntity) be).getLootTable() == null) continue;
                    if (be.getBlockPos().distSqr(at) > (double) r * r) continue;
                    out.add(be.getBlockPos().immutable());
                }
            }
        }
        return out;
    }

    /**
     * The parts of the traps within six blocks of a chest: tripwire hooks, dispensers, tripwire string, and pressure
     * plates with TNT under them. TNT with nothing to set it off is no trap.
     */
    static List<BlockPos> trapParts(ServerLevel level, BlockPos chest) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(chest.offset(-6, -5, -6), chest.offset(6, 5, 6))) {
            if (isTrapPart(level, p)) out.add(p.immutable());
        }
        return out;
    }

    static boolean isTrapPart(ServerLevel level, BlockPos p) {
        var st = level.getBlockState(p);
        return st.is(Blocks.TRIPWIRE_HOOK) || st.is(Blocks.DISPENSER) || st.is(Blocks.TRIPWIRE)
                || st.is(net.minecraft.tags.BlockTags.PRESSURE_PLATES) && com.mineagent.engine.pathing.util.BlockHelper.tntUnder(level, p);
    }

    /** Hooks first (their string then sets nothing off), the dispensers, the string, the plates; nearest first within each. */
    private static void order(List<BlockPos> parts, ServerLevel level) {
        parts.sort(java.util.Comparator.comparingInt((BlockPos p) -> {
            var st = level.getBlockState(p);
            return st.is(Blocks.TRIPWIRE_HOOK) ? 0 : st.is(Blocks.DISPENSER) ? 1 : st.is(Blocks.TRIPWIRE) ? 2 : 3;
        }));
    }

    /** Its feet on (or in) that block. */
    private static boolean standingOn(ServerPlayer sp, BlockPos p) {
        BlockPos at = sp.blockPosition();
        return at.equals(p) || at.below().equals(p);
    }

    private net.minecraft.world.entity.vehicle.MinecartChest triedCart;
    private final Map<BlockPos, Integer> partTries = new java.util.HashMap<>();
    private final java.util.Set<BlockPos> gone = new java.util.HashSet<>();

    /**
     * A part it couldn't get at yet (one behind another, a hook behind its dispenser): tried again after the others; a
     * second time, it is given up on.
     */
    private void later(BlockPos q) {
        int tries = partTries.merge(q, 1, Integer::sum);
        if (tries < 2) parts.add(q);
        else stuckParts.add(q);
        part++;
        ticks = 0;
    }
    private int clearTicks;

    /** The nearest piglin within 16 of a block. */
    private static net.minecraft.world.entity.monster.piglin.AbstractPiglin nearestPiglin(ServerLevel level, BlockPos at) {
        net.minecraft.world.entity.monster.piglin.AbstractPiglin best = null;
        for (var p : level.getEntitiesOfClass(net.minecraft.world.entity.monster.piglin.AbstractPiglin.class,
                new net.minecraft.world.phys.AABB(at).inflate(16.0), net.minecraft.world.entity.LivingEntity::isAlive)) {
            if (best == null || p.distanceToSqr(at.getCenter()) < best.distanceToSqr(at.getCenter())) best = p;
        }
        return best;
    }

    /** The nearest structure of {@link #SITES} within 100 chunks it hasn't looted, or null. */
    private BlockPos nearestSite(ServerPlayer sp, ServerLevel level) {
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<net.minecraft.core.Holder<Structure>> holders = new ArrayList<>();
        for (String id : record.sites != null ? record.sites : SITES) {
            registry.getHolder(ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse(id))).ifPresent(holders::add);
        }
        if (holders.isEmpty()) return null;
        HolderSet<Structure> set = HolderSet.direct(holders);
        var loop = TaskContext.agentLoop(player);
        String dim = level.dimension().location().toString();
        BlockPos from = sp.blockPosition();
        // The nearest; when it was looted already, the nearest beyond it, a few times over.
        for (int tries = 0; tries < 6; tries++) {
            var match = level.getChunkSource().getGenerator().findNearestMapStructure(level, set, from, 100, false);
            if (match == null) return null;
            BlockPos at = match.getFirst();
            if (loop == null || !loop.looted(dim, at)) {
                // Under the ground, or in the Nether (its surface is the roof): to the middle of the structure itself.
                underground = level.dimension() == net.minecraft.world.level.Level.NETHER
                        || match.getSecond().unwrapKey().map(k -> UNDERGROUND.contains(k.location().toString())).orElse(false);
                if (underground) {
                    BlockPos middle = middleOf(level, at, match.getSecond().value());
                    if (middle != null) at = middle;
                }
                return at;
            }
            double dx = at.getX() - sp.getX(), dz = at.getZ() - sp.getZ();
            double len = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
            from = new BlockPos((int) (at.getX() + dx / len * 96), at.getY(), (int) (at.getZ() + dz / len * 96));
        }
        return null;
    }

    /** The middle of the structure that starts in {@code at}'s chunk (its start read as the game made it); null if none. */
    private static BlockPos middleOf(ServerLevel level, BlockPos at, Structure structure) {
        try {
            var chunk = level.getChunk(at.getX() >> 4, at.getZ() >> 4, net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS, true);
            var start = chunk == null ? null : chunk.getAllStarts().get(structure);
            if (start == null || !start.isValid()) return null;
            var box = start.getBoundingBox();
            return new BlockPos(box.getCenter());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void say(String s) {
        var loop = TaskContext.agentLoop(player);
        if (loop != null) loop.say(s);
    }

    private TaskState end(boolean ok, String why) {
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
        ServerPlayer sp = TaskContext.serverPlayer(player);
        if (sp.containerMenu != sp.inventoryMenu) sp.closeContainer();
        phase = Phase.DONE;
        List<String> parts = new ArrayList<>();
        got.entrySet().stream().sorted((a, b) -> Integer.compare(b.getValue(), a.getValue())).limit(8)
                .forEach(e -> parts.add(e.getValue() + " " + com.mineagent.engine.planning.production.ProductionPlanner.plural(e.getKey(), e.getValue())));
        outcome = opened == 0 ? "No loot" + (why == null ? "" : ": " + why)
                : "Looted " + opened + (opened == 1 ? " chest" : " chests") + ": " + String.join(", ", parts)
                + (skipped > 0 ? "; left " + skipped + " with traps by them" : "") + (why == null ? "" : "; " + why);
        say(outcome + ".");
        var loop = TaskContext.agentLoop(player);
        if (loop != null) loop.journal().record("loot", outcome, sp.level().getGameTime(), sp.level().dimension().location().toString(),
                sp.getBlockX(), sp.getBlockY(), sp.getBlockZ());
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
        ServerPlayer sp = TaskContext.serverPlayer(player);
        if (sp.containerMenu != sp.inventoryMenu) sp.closeContainer();
    }
}
