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
 * looting"). Each opened as a player opens one and everything taken. A trapped chest, and any chest with TNT, a
 * tripwire, a dispenser or a stone pressure plate by it (a desert temple's floor, a jungle temple's), is left alone and
 * said so.
 */
public class LootTask extends CompanionTask<LootTask.LootRecord> {
    public static final class LootRecord extends TaskRecord {
        /** Go to the nearest structure worth looting first (else: round where it stands). */
        public final boolean go;

        public LootRecord(String toolCallId, boolean go) {
            super(toolCallId);
            this.go = go;
        }
    }

    /** How far round it looks for chests. */
    static final int RADIUS = 48;
    /** The structures it goes looting at: on the surface, no boss, no warden, no traps it can't see. */
    static final List<String> SITES = List.of("minecraft:ruined_portal", "minecraft:ruined_portal_desert", "minecraft:ruined_portal_jungle",
            "minecraft:ruined_portal_swamp", "minecraft:ruined_portal_mountain", "minecraft:village_plains", "minecraft:village_desert",
            "minecraft:village_savanna", "minecraft:village_snowy", "minecraft:village_taiga", "minecraft:igloo", "minecraft:shipwreck_beached");

    private enum Phase { FIND, TRAVEL, SCAN, OPEN, DONE }

    private PlayerNav nav;
    private Phase phase = Phase.FIND;
    private BlockPos site;
    private final List<BlockPos> chests = new ArrayList<>();
    private int index, ticks, opened, skipped;
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
                    if (site == null) return end(false, "there's no ruined portal, village, igloo or wreck within 1,600 blocks I haven't looted");
                    say("Off looting: the nearest place worth it, at " + site.getX() + ", " + site.getZ() + ", "
                            + (int) Math.sqrt(site.distSqr(sp.blockPosition())) + " blocks away.");
                    phase = Phase.TRAVEL;
                    ticks = 0;
                    return TaskState.RUNNING;
                }
                case TRAVEL -> {
                    double dx = sp.getX() - site.getX(), dz = sp.getZ() - site.getZ();
                    if (dx * dx + dz * dz <= 24.0 * 24.0) {
                        nav.cancel();
                        phase = Phase.SCAN;
                        return TaskState.RUNNING;
                    }
                    if (ticks > 36_000) return end(false, "I couldn't get there");
                    if (ticks % 100 == 1 || !nav.isNavigating()) {
                        int y = level.isLoaded(site) ? level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                site.getX(), site.getZ()) : sp.getBlockY();
                        nav.navigateNear(site.getX(), y, site.getZ(), 8);
                    }
                    nav.tick();
                    return TaskState.RUNNING;
                }
                case SCAN -> {
                    chests.clear();
                    for (BlockPos p : unopened(level, site, RADIUS)) {
                        if (trapped(level, p)) {
                            skipped++;
                            continue;
                        }
                        chests.add(p);
                    }
                    chests.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(sp.blockPosition())));
                    var loop = TaskContext.agentLoop(player);
                    if (loop != null && record.go) loop.noteLooted(level.dimension().location().toString(), site);
                    if (chests.isEmpty()) return end(opened > 0 || skipped == 0 && !record.go,
                            skipped > 0 ? "the only chests here have traps by them" : "there are no unopened chests here");
                    phase = Phase.OPEN;
                    index = 0;
                    ticks = 0;
                    return TaskState.RUNNING;
                }
                case OPEN -> {
                    if (index >= chests.size()) return end(true, null);
                    if (full(sp)) return end(opened > 0, "my pack is full");
                    BlockPos c = chests.get(index);
                    if (sp.containerMenu != sp.inventoryMenu) {
                        // Open (its loot was made as it opened): all of it into the pack.
                        takeAll(sp, sp.containerMenu);
                        sp.closeContainer();
                        opened++;
                        index++;
                        ticks = 0;
                        return TaskState.RUNNING;
                    }
                    if (!(level.getBlockEntity(c) instanceof RandomizableContainerBlockEntity rc) || rc.getLootTable() == null) {
                        // Opened since by someone else, or gone.
                        index++;
                        ticks = 0;
                        return TaskState.RUNNING;
                    }
                    if (!sp.canInteractWithBlock(c, 0.0)) {
                        if (ticks > 900) { // no way to it
                            index++;
                            ticks = 0;
                            return TaskState.RUNNING;
                        }
                        if (ticks % 40 == 1 || !nav.isNavigating()) nav.navigateNear(c.getX(), c.getY(), c.getZ(), 2);
                        nav.tick();
                        return TaskState.RUNNING;
                    }
                    nav.cancel();
                    TaskContext.inputDriver(player).clear();
                    if (!com.mineagent.engine.act.Interaction.openContainer(sp, c) && ++ticks > 60) {
                        index++;
                        ticks = 0;
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

    /** TNT, a tripwire, a dispenser or a stone pressure plate within five blocks of it: a trap, left alone. */
    static boolean trapped(ServerLevel level, BlockPos chest) {
        for (BlockPos p : BlockPos.betweenClosed(chest.offset(-5, -5, -5), chest.offset(5, 5, 5))) {
            var st = level.getBlockState(p);
            if (st.is(Blocks.TNT) || st.is(Blocks.TRIPWIRE) || st.is(Blocks.TRIPWIRE_HOOK) || st.is(Blocks.DISPENSER)
                    || st.is(Blocks.STONE_PRESSURE_PLATE)) return true;
        }
        return false;
    }

    /** The nearest structure of {@link #SITES} within 100 chunks it hasn't looted, or null. */
    private BlockPos nearestSite(ServerPlayer sp, ServerLevel level) {
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<net.minecraft.core.Holder<Structure>> holders = new ArrayList<>();
        for (String id : SITES) {
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
            if (loop == null || !loop.looted(dim, at)) return at;
            double dx = at.getX() - sp.getX(), dz = at.getZ() - sp.getZ();
            double len = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
            from = new BlockPos((int) (at.getX() + dx / len * 96), at.getY(), (int) (at.getZ() + dz / len * 96));
        }
        return null;
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
