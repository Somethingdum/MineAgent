package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskState;
import com.mineagent.api.task.TaskSnapshot;
import com.mineagent.engine.pathing.cache.PathCaches;
import com.mineagent.engine.pathing.execute.PlayerNav;
import com.mineagent.engine.planning.IntentAwareTask;
import com.mineagent.engine.planning.IntentContract;
import com.mineagent.engine.world.WorldAssetIndex;
import com.mineagent.tools.block.AutoMineTool;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Finds reachable matching blocks and mines them through vanilla progress. */
public class MineBlockTask extends CompanionTask<AutoMineTool.MineBlockTaskRecord>
        implements IntentAwareTask {

    /** Independent guard in case navigation fails to publish a terminal event. */
    private static final long NAVIGATION_STALL_TICKS = 8L * 20L;
    private static final int MAX_CONSECUTIVE_TARGET_FAILURES = 12;

    private enum Phase { SCAN, NAVIGATE, DIG, DONE }

    private PlayerNav nav;
    private Phase phase;
    private int minedCount;
    private List<BlockPos> targetBlocks;
    private int currentBlockIndex;
    private int digTicks;
    private int digTimeoutTicks;
    private BlockPos activeBreakTarget;
    private String failReason;
    private String lastNavigationFailure;
    private final Set<BlockPos> unreachableBlocks = new HashSet<>();
    private BlockScanner.ScanSession scanSession;
    private long lastProgressTick;
    private long progressRevision;
    private long lastExecutorProgressVersion;
    private Vec3 lastProgressPosition;
    private int consecutiveTargetFailures;

    public MineBlockTask(AgentPlayer player, AutoMineTool.MineBlockTaskRecord record) {
        super(player, record);
    }

    @Override
    public void onStart() {
        phase = Phase.SCAN;
        minedCount = 0;
        currentBlockIndex = 0;
        digTicks = 0;
        digTimeoutTicks = Integer.MAX_VALUE;
        activeBreakTarget = null;
        failReason = null;
        lastNavigationFailure = null;
        unreachableBlocks.clear();
        long now = TaskContext.serverPlayer(player).level().getGameTime();
        lastProgressTick = now;
        progressRevision = 1L;
        lastExecutorProgressVersion = Long.MIN_VALUE;
        lastProgressPosition = TaskContext.serverPlayer(player).position();
        consecutiveTargetFailures = 0;

        setupNavigation();
        beginScan();
    }

    @Override
    protected void onResume() {
        // Preserve executor-verified minedCount and the unreachable set, but
        // discard the interrupted scan/path/break operation. The new scan is
        // grounded in the current world and cannot resume stale coordinates.
        digTicks = 0;
        digTimeoutTicks = Integer.MAX_VALUE;
        activeBreakTarget = null;
        failReason = null;
        lastNavigationFailure = null;
        markProgress(TaskContext.serverPlayer(player).level().getGameTime());
        setupNavigation();
        beginScan();
    }

    private void setupNavigation() {
        PathCaches caches = TaskContext.navCaches(player);
        nav = new PlayerNav(player, caches, intentContract().terrainPolicy());
        nav.setListener(new PlayerNav.NavListener() {
            @Override
            public void onGoalReached() {
                if (phase == Phase.NAVIGATE) {
                    phase = Phase.DIG;
                    markProgress(TaskContext.serverPlayer(player).level().getGameTime());
                }
            }

            @Override
            public void onNavigationFailed(String reason) {
                lastNavigationFailure = "Navigation failed: " + reason;
                if (recordTargetFailure(currentTarget(), lastNavigationFailure)) return;
                beginScan();
            }
        });
    }

    private void beginScan() {
        var sp = TaskContext.serverPlayer(player);
        var pos = sp.blockPosition();
        // A fresh target search owns a fresh break timer. Carrying digTicks
        // across a navigation failure made the next valid block inherit the
        // previous target's elapsed time and fail prematurely.
        digTicks = 0;
        digTimeoutTicks = Integer.MAX_VALUE;
        activeBreakTarget = null;
        scanSession = BlockScanner.begin(sp.serverLevel(), pos.getX(), pos.getY(), pos.getZ(),
                record.radius, record.blockType);
        targetBlocks = null;
        currentBlockIndex = 0;
        phase = Phase.SCAN;
        lastExecutorProgressVersion = Long.MIN_VALUE;
        markProgress(sp.level().getGameTime());
    }

    private void finishScan() {
        targetBlocks = scanSession.results().stream()
                .filter(block -> !unreachableBlocks.contains(block))
                .toList();
        scanSession = null;
        if (targetBlocks.isEmpty()) {
            if (minedCount > 0) {
                failReason = "Only mined " + minedCount + "/" + record.count
                        + " blocks of '" + record.blockType
                        + "' before no reachable targets remained";
            } else {
                failReason = "No reachable blocks of type '" + record.blockType
                        + "' found within radius " + record.radius
                        + (lastNavigationFailure == null ? ""
                        : "; last_navigation_failure=" + lastNavigationFailure);
            }
            phase = Phase.DONE;
            markProgress(TaskContext.serverPlayer(player).level().getGameTime());
            return;
        }
        currentBlockIndex = 0;
    }

    @Override
    public TaskState onTick() {
        long gameTime = TaskContext.serverPlayer(player).level().getGameTime();
        if (record.deadline() > 0L && gameTime >= record.deadline()) {
            cancelNav();
            return TaskState.FAILED;
        }

        switch (phase) {
            case SCAN -> tickScan();
            case NAVIGATE -> nav.tick();
            case DIG -> tickDig();
            case DONE -> { }
        }

        observeNavigationProgress(gameTime);
        if (phase == Phase.NAVIGATE
                && gameTime - lastProgressTick > NAVIGATION_STALL_TICKS) {
            lastNavigationFailure = "Navigation produced no physical or path progress for "
                    + (gameTime - lastProgressTick) + " ticks at player="
                    + TaskContext.serverPlayer(player).blockPosition().toShortString();
            nav.cancel();
            if (!recordTargetFailure(currentTarget(), lastNavigationFailure)) beginScan();
        }

        if (phase == Phase.DONE && failReason != null) return TaskState.FAILED;
        if (minedCount >= record.count && tree == null) return TaskState.SUCCESS; // a tree begun is felled whole first
        return TaskState.RUNNING;
    }

    private void tickScan() {
        if (scanSession == null) beginScan();
        // Bound the radius scan so a large request cannot monopolize a tick.
        scanSession.scan(4096);
        if (!scanSession.isComplete()) return;
        finishScan();
        if (phase != Phase.DONE) navigateToNextBlock();
    }

    private void tickDig() {
        if (targetBlocks == null || currentBlockIndex >= targetBlocks.size()) {
            beginScan();
            return;
        }

        BlockPos target = targetBlocks.get(currentBlockIndex);
        var sp = TaskContext.serverPlayer(player);
        var level = sp.serverLevel();

        if (level.getBlockState(target).isAir()) {
            // Only count disappearance after this task owned the break target.
            if (target.equals(activeBreakTarget)) {
                if (!leafPass) minedCount++;
                afterBreak(sp, target);
            }
            activeBreakTarget = null;
            invalidateWorldAsset(target);
            if (minedCount > 0) consecutiveTargetFailures = 0;
            markProgress(level.getGameTime());
            advanceTarget();
            return;
        }

        if (activeBreakTarget == null) {
            int durability = BlockDigger.toolDurability(sp);
            if (durability != Integer.MAX_VALUE && durability <= 1) {
                failReason = "No safe tool available (held durability: " + durability + ")";
                phase = Phase.DONE;
                return;
            }
            BlockDigger.BreakAssessment assessment =
                    BlockDigger.startBreakingDetailed(sp, target);
            if (!assessment.allowed()) {
                if (!recordTargetFailure(target,
                        "Could not start vanilla break at " + target.toShortString()
                                + "; " + assessment.diagnostic())) advanceTarget();
                return;
            }
            digTimeoutTicks = BlockDigger.expectedBreakTicks(sp, target);
            activeBreakTarget = target;
            breakingState = level.getBlockState(target);
            markProgress(level.getGameTime());
        }

        digTicks++;
        if (level.getBlockState(target).isAir()) {
            if (!leafPass) minedCount++;
            afterBreak(sp, target);
            consecutiveTargetFailures = 0;
            activeBreakTarget = null;
            invalidateWorldAsset(target);
            markProgress(level.getGameTime());
            advanceTarget();
        } else if (digTicks > digTimeoutTicks) {
            BlockDigger.abortBreaking(sp, target);
            activeBreakTarget = null;
            if (!recordTargetFailure(target,
                    "Vanilla break timed out at " + target.toShortString()
                            + " after " + digTicks + " ticks")) advanceTarget();
        }
    }

    /** Stop boundedly when local evidence says this goal is not executable. */
    private boolean recordTargetFailure(BlockPos target, String reason) {
        if (target != null) unreachableBlocks.add(target.immutable());
        consecutiveTargetFailures++;
        if (consecutiveTargetFailures < MAX_CONSECUTIVE_TARGET_FAILURES) return false;
        failReason = "Mining stopped after " + consecutiveTargetFailures
                + " consecutive unreachable targets; last_failure=" + reason;
        phase = Phase.DONE;
        markProgress(TaskContext.serverPlayer(player).level().getGameTime());
        return true;
    }

    private void advanceTarget() {
        currentBlockIndex++;
        digTicks = 0;
        markProgress(TaskContext.serverPlayer(player).level().getGameTime());
        // A tree is felled whole (its owner: "it should get ALL of the wood"), then its saplings, then planted.
        BlockPos next = nextOfTree(TaskContext.serverPlayer(player));
        if (next != null) {
            targetBlocks = new java.util.ArrayList<>(java.util.List.of(next));
            currentBlockIndex = 0;
            navigateToNextBlock();
            return;
        }
        if (minedCount >= record.count) {
            cancelNav();
        } else if (targetBlocks == null || currentBlockIndex >= targetBlocks.size()) {
            beginScan();
        } else {
            navigateToNextBlock();
        }
    }

    /**
     * For a log two or more above the ground, the cell at the foot of its trunk (the logs under it gone, or still to cut,
     * down to solid ground) when it is free to stand in and the log is in reach from there; else null.
     */
    /** What the block it is breaking was, for the replanting. */
    private net.minecraft.world.level.block.state.BlockState breakingState;

    /** The sapling for each log, for the trees it fells. */
    private static final java.util.Map<net.minecraft.world.level.block.Block, net.minecraft.world.level.block.Block> SAPLINGS = java.util.Map.of(
            net.minecraft.world.level.block.Blocks.OAK_LOG, net.minecraft.world.level.block.Blocks.OAK_SAPLING,
            net.minecraft.world.level.block.Blocks.SPRUCE_LOG, net.minecraft.world.level.block.Blocks.SPRUCE_SAPLING,
            net.minecraft.world.level.block.Blocks.BIRCH_LOG, net.minecraft.world.level.block.Blocks.BIRCH_SAPLING,
            net.minecraft.world.level.block.Blocks.JUNGLE_LOG, net.minecraft.world.level.block.Blocks.JUNGLE_SAPLING,
            net.minecraft.world.level.block.Blocks.ACACIA_LOG, net.minecraft.world.level.block.Blocks.ACACIA_SAPLING,
            net.minecraft.world.level.block.Blocks.DARK_OAK_LOG, net.minecraft.world.level.block.Blocks.DARK_OAK_SAPLING,
            net.minecraft.world.level.block.Blocks.CHERRY_LOG, net.minecraft.world.level.block.Blocks.CHERRY_SAPLING);

    // ── Whole trees (SMH plan 70: "get ALL of the wood, and then replant them, with conscious spacing") ──

    /** The tree it is felling: its logs (still to cut, or cut), its natural leaves, its stump, and whether it is at the leaves. */
    private java.util.List<BlockPos> tree, treeLeaves;
    private boolean leafPass;
    private int leavesBroken;
    /** Leaves broken for saplings, at most. */
    static final int LEAVES_FOR_SAPLINGS = 40;

    private void afterBreak(net.minecraft.server.level.ServerPlayer sp, BlockPos at) {
        var was = breakingState;
        if (leafPass) {
            breakingState = null;
            leavesBroken++;
            return;
        }
        if (tree == null && was != null && was.is(net.minecraft.tags.BlockTags.LOGS) && com.mineagent.engine.settings.Switches.wholeTrees()) {
            survey(sp.serverLevel(), at);
        }
        replant(sp, at);
    }

    /**
     * The tree a log was part of: the logs joined to it (any way, corners too) within 8 blocks across and 40 up, none a
     * player placed; a tree when natural leaves touch them (a log cabin has none). Kept for the felling.
     */
    private void survey(net.minecraft.server.level.ServerLevel level, BlockPos from) {
        var placed = com.mineagent.engine.world.PlacedBlocks.of(level);
        java.util.ArrayDeque<BlockPos> todo = new java.util.ArrayDeque<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        java.util.List<BlockPos> logs = new java.util.ArrayList<>();
        java.util.Set<BlockPos> leaves = new java.util.LinkedHashSet<>();
        seen.add(from);
        todo.add(from);
        while (!todo.isEmpty() && logs.size() < 160) {
            BlockPos p = todo.poll();
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos q = p.offset(dx, dy, dz);
                if (!seen.add(q) || Math.abs(q.getX() - from.getX()) > 8 || Math.abs(q.getZ() - from.getZ()) > 8
                        || q.getY() < from.getY() - 3 || q.getY() > from.getY() + 40) continue;
                var st = level.getBlockState(q);
                if (st.is(net.minecraft.tags.BlockTags.LOGS) && !placed.isPlaced(q.getX(), q.getY(), q.getZ())) {
                    logs.add(q.immutable());
                    todo.add(q);
                } else if (st.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock
                        && !st.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT) && leaves.size() < 96) {
                    leaves.add(q.immutable());
                }
            }
        }
        if (leaves.size() < 3) return; // not a tree: a built thing of logs, left as it is
        logs.sort(java.util.Comparator.comparingInt(BlockPos::getY));
        tree = logs;
        treeLeaves = new java.util.ArrayList<>(leaves);
    }

    /**
     * The next block of the tree to cut: its lowest log still standing (cut from the bottom up, as a player does); then,
     * with no sapling of its kind on it to plant, a leaf, for the saplings; then none, and it plants.
     */
    private BlockPos nextOfTree(net.minecraft.server.level.ServerPlayer sp) {
        if (tree == null) return null;
        var level = sp.serverLevel();
        for (BlockPos p : tree) {
            if (level.getBlockState(p).is(net.minecraft.tags.BlockTags.LOGS) && !unreachableBlocks.contains(p)) return p;
        }
        var sapling = pendingSapling;
        boolean need = sapling != null && com.mineagent.engine.settings.Switches.replantTrees()
                && ProductionTask.count(sp, sapling.asItem()) < saplingsFor(sapling);
        if (need && leavesBroken < LEAVES_FOR_SAPLINGS) {
            leafPass = true;
            BlockDigger.forSaplings = true;
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            for (BlockPos p : treeLeaves) {
                var st = level.getBlockState(p);
                if (!(st.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock) || unreachableBlocks.contains(p)) continue;
                double d = p.distToCenterSqr(sp.getEyePosition());
                if (d < bestD) {
                    bestD = d;
                    best = p;
                }
            }
            if (best != null) return best;
        }
        // Down, and its saplings had: planted, spaced.
        leafPass = false;
        BlockDigger.forSaplings = false;
        tree = null;
        treeLeaves = null;
        replant(sp, null);
        return null;
    }

    /** Saplings a tree of this kind needs: four for a dark oak (it grows only from a square of four), else one. */
    static int saplingsFor(net.minecraft.world.level.block.Block sapling) {
        return sapling == net.minecraft.world.level.block.Blocks.DARK_OAK_SAPLING ? 4 : 1;
    }

    /** Open air over a sapling that tree needs to grow into. */
    static int roomFor(net.minecraft.world.level.block.Block sapling) {
        if (sapling == net.minecraft.world.level.block.Blocks.SPRUCE_SAPLING || sapling == net.minecraft.world.level.block.Blocks.JUNGLE_SAPLING) return 10;
        if (sapling == net.minecraft.world.level.block.Blocks.ACACIA_SAPLING || sapling == net.minecraft.world.level.block.Blocks.DARK_OAK_SAPLING) return 8;
        return 7;
    }

    /**
     * Where a sapling goes so it grows: earth under it, the cell itself open, room above it for the tree, and no other
     * sapling or trunk within 2 blocks either way (its owner: "with conscious spacing"); the stump first, else the nearest
     * such spot within 3 of it. Null when there is none.
     */
    static BlockPos spacedSpot(net.minecraft.server.level.ServerLevel level, BlockPos stump, net.minecraft.world.level.block.Block sapling) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = -1; dy <= 1; dy++) {
            BlockPos p = stump.offset(dx, dy, dz);
            if (!level.getBlockState(p).canBeReplaced() || !level.getFluidState(p).isEmpty()
                    || !level.getBlockState(p.below()).is(net.minecraft.tags.BlockTags.DIRT)) continue;
            boolean room = true;
            for (int up = 1; up <= roomFor(sapling) && room; up++) {
                var st = level.getBlockState(p.above(up));
                room = st.isAir() || st.is(net.minecraft.tags.BlockTags.LEAVES) || st.canBeReplaced();
            }
            if (!room || crowded(level, p)) continue;
            double d = p.distSqr(stump);
            if (d < bestD) {
                bestD = d;
                best = p.immutable();
            }
        }
        return best;
    }

    /** A sapling or a trunk within 2 blocks of this one, either way (corners too). */
    static boolean crowded(net.minecraft.server.level.ServerLevel level, BlockPos p) {
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = -1; dy <= 1; dy++) {
            if (dx == 0 && dz == 0 && dy == 0) continue;
            var st = level.getBlockState(p.offset(dx, dy, dz));
            if (st.is(net.minecraft.tags.BlockTags.SAPLINGS) || st.is(net.minecraft.tags.BlockTags.LOGS)) return true;
        }
        return false;
    }

    /**
     * Leaving no trace (SMH plan 70, MA9.4; {@code behaviour.replantTrees}): the foot of a tree it felled, on earth,
     * gets a sapling of its kind when it carries one, spaced so it grows; {@code at} null: the tree is down, plant now.
     */
    private void replant(net.minecraft.server.level.ServerPlayer sp, BlockPos at) {
        if (at == null) {
            plantSpaced(sp);
            return;
        }
        var was = breakingState;
        breakingState = null;
        if (!com.mineagent.engine.settings.Switches.replantTrees()) return;
        var level = sp.serverLevel();
        if (was != null && pendingBase == null && SAPLINGS.containsKey(was.getBlock())
                && level.getBlockState(at.below()).is(net.minecraft.tags.BlockTags.DIRT)) {
            pendingBase = at.immutable();
            pendingSapling = SAPLINGS.get(was.getBlock());
        }
        if (pendingBase == null || tree != null) return; // a whole tree: planted when it is all down
        // Only once the tree is down: a sapling at the trunk's foot fills the cell it stands in to reach the high logs.
        for (int up = 1; up <= 12; up++) {
            if (level.getBlockState(pendingBase.above(up)).is(net.minecraft.tags.BlockTags.LOGS)) return;
        }
        plantSpaced(sp);
    }

    /** The sapling (or a dark oak's four) put down where it will grow, near where the tree stood. */
    private void plantSpaced(net.minecraft.server.level.ServerPlayer sp) {
        BlockPos stump = pendingBase;
        var sapling = pendingSapling;
        pendingBase = null;
        if (stump == null || sapling == null || !com.mineagent.engine.settings.Switches.replantTrees()) return;
        var level = sp.serverLevel();
        if (ProductionTask.count(sp, sapling.asItem()) < saplingsFor(sapling)) return;
        if (sapling == net.minecraft.world.level.block.Blocks.DARK_OAK_SAPLING) {
            // A square of four where the trunk stood (dark oaks grow from nothing less).
            for (BlockPos p : new BlockPos[] {stump, stump.east(), stump.south(), stump.east().south()}) {
                if (level.getBlockState(p).canBeReplaced() && level.getBlockState(p.below()).is(net.minecraft.tags.BlockTags.DIRT)) {
                    com.mineagent.engine.act.Placement.placeBlock(sp, p, sapling.defaultBlockState());
                }
            }
            return;
        }
        BlockPos spot = spacedSpot(level, stump, sapling);
        if (spot != null) com.mineagent.engine.act.Placement.placeBlock(sp, spot, sapling.defaultBlockState());
    }

    /** The foot of the tree it is felling, to plant when the last log above it is down. */
    private BlockPos pendingBase;
    private net.minecraft.world.level.block.Block pendingSapling;

    private BlockPos trunkBase(BlockPos log) {
        var level = TaskContext.serverPlayer(player).serverLevel();
        if (!level.getBlockState(log).is(net.minecraft.tags.BlockTags.LOGS)) return null;
        BlockPos p = log.below();
        int depth = 0;
        while (depth < 8 && (level.getBlockState(p).isAir() || level.getBlockState(p).is(net.minecraft.tags.BlockTags.LOGS))) {
            p = p.below();
            depth++;
        }
        if (depth < 2 || !com.mineagent.engine.pathing.util.BlockHelper.canStandOn(level.getBlockState(p))) return null;
        BlockPos feet = p.above();
        if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) return null; // still log there: cut first
        return log.getY() - feet.getY() <= 5 ? feet : null;
    }

    private void navigateToNextBlock() {
        if (targetBlocks == null || currentBlockIndex >= targetBlocks.size()) {
            beginScan();
            return;
        }
        BlockPos target = targetBlocks.get(currentBlockIndex);
        // A tree's log above head height: stood under it, at the foot of the trunk, as a player fells a tree (SMH plan
        // 70, MA7.6); beside it there is nowhere to stand.
        BlockPos base = trunkBase(target);
        if (base != null) {
            nav.navigateTo(base.getX(), base.getY(), base.getZ());
            phase = Phase.NAVIGATE;
            lastExecutorProgressVersion = Long.MIN_VALUE;
            markProgress(TaskContext.serverPlayer(player).level().getGameTime());
            return;
        }
        nav.navigateToBlock(target.getX(), target.getY(), target.getZ());
        phase = Phase.NAVIGATE;
        lastExecutorProgressVersion = Long.MIN_VALUE;
        markProgress(TaskContext.serverPlayer(player).level().getGameTime());
    }

    /**
     * Treat only executor evidence as progress. Phase labels alone previously
     * kept a task looking healthy while the body remained at one coordinate.
     */
    private void observeNavigationProgress(long gameTime) {
        if (phase != Phase.NAVIGATE || nav == null) return;
        var sp = TaskContext.serverPlayer(player);
        Vec3 position = sp.position();
        if (lastProgressPosition == null
                || position.distanceToSqr(lastProgressPosition) >= 0.04) {
            lastProgressPosition = position;
            markProgress(gameTime);
        }
        var executor = nav.core().executor();
        if (executor != null && executor.progressVersion() != lastExecutorProgressVersion) {
            lastExecutorProgressVersion = executor.progressVersion();
            markProgress(gameTime);
        }
    }

    private void markProgress(long gameTime) {
        lastProgressTick = gameTime;
        progressRevision++;
    }

    private void invalidateWorldAsset(BlockPos target) {
        var loop = TaskContext.agentLoop(player);
        if (loop == null || target == null) return;
        var sp = TaskContext.serverPlayer(player);
        loop.worldAssetIndex().invalidatePosition(new WorldAssetIndex.Position(
                sp.level().dimension().location().toString(), target.getX(),
                target.getY(), target.getZ()));
    }

    private BlockPos currentTarget() {
        return targetBlocks != null
                && currentBlockIndex >= 0 && currentBlockIndex < targetBlocks.size()
                ? targetBlocks.get(currentBlockIndex) : null;
    }

    private void cancelNav() {
        if (nav != null) nav.cancel();
        if (activeBreakTarget != null) {
            var sp = TaskContext.serverPlayer(player);
            // A block can finish between task ticks because vanilla advances
            // ServerPlayerGameMode independently. Preserve that verified
            // completion before clearing the progressive break owner.
            if (sp.level().getBlockState(activeBreakTarget).isAir()) {
                minedCount++;
            } else {
                BlockDigger.abortBreaking(sp, activeBreakTarget);
            }
            activeBreakTarget = null;
        }
        TaskContext.inputDriver(player).clear();
    }

    @Override
    public void onInterrupt() {
        cancelNav();
        BlockDigger.forSaplings = false; // shears are fine again for anyone else's leaves
    }

    @Override
    public TaskSnapshot snapshot() {
        BlockPos target = currentTarget();
        String stage = phase == null ? "initializing"
                : phase.name().toLowerCase(java.util.Locale.ROOT);
        var sp = TaskContext.serverPlayer(player);
        long stagnantTicks = Math.max(0L, sp.level().getGameTime() - lastProgressTick);
        StringBuilder evidence = new StringBuilder("player=")
                .append(sp.blockPosition().toShortString())
                .append(" stagnant_ticks=").append(stagnantTicks);
        if (nav != null) {
            evidence.append(" nav_state=").append(nav.state());
            var executor = nav.core().executor();
            if (executor != null) {
                evidence.append(" movement=").append(executor.currentMovementIndex())
                        .append('/').append(executor.path().length())
                        .append(" movement_ticks=").append(executor.ticksOnCurrentMovement())
                        .append(" movement_stagnant_ticks=")
                        .append(executor.ticksWithoutProgress());
            }
            if (nav.core().lastFailureDetail() != null) {
                evidence.append(" last_path_failure=")
                        .append(nav.core().lastFailureDetail());
            }
        }
        if (activeBreakTarget != null) {
            evidence.append(" vanilla_break_target=").append(activeBreakTarget)
                    .append(" break_ticks=").append(digTicks)
                    .append('/').append(digTimeoutTicks);
        }
        return TaskSnapshot.progress(stage,
                phase == Phase.DONE ? "Mining finished" : "Mining " + record.blockType,
                minedCount, record.count,
                target == null ? null : target.getX(),
                target == null ? null : target.getY(),
                target == null ? null : target.getZ(),
                phase == Phase.DONE ? failReason : null,
                evidence.toString(), progressRevision);
    }

    @Override
    public IntentContract intentContract() {
        String id = record.blockType == null ? "" : record.blockType.toLowerCase(
                java.util.Locale.ROOT);
        boolean treeMaterial = id.contains("_log") || id.contains("_wood")
                || id.endsWith(":log") || id.endsWith(":wood");
        IntentContract.TerrainPolicy policy = new IntentContract.TerrainPolicy(
                true, !treeMaterial, !treeMaterial, false,
                treeMaterial ? 0 : 8, 12, treeMaterial ? 3 : 12,
                IntentContract.CleanupMode.CONTEXTUAL);
        return new IntentContract("Mine " + record.count + " blocks of " + record.blockType,
                "The requested blocks disappear through vanilla breaking and are counted once",
                null, null, null, policy, java.util.List.of(
                new IntentContract.Constraint("target_material",
                        IntentContract.ConstraintKind.HARD,
                        "Only requested blocks count as task output; other breaks are navigation obstacles",
                        "mining"),
                new IntentContract.Constraint("tree_topology",
                        treeMaterial ? IntentContract.ConstraintKind.HARD
                                : IntentContract.ConstraintKind.PREFERENCE,
                        treeMaterial ? "Do not pillar or bridge merely to chop this tree"
                                : "Minimize non-target terrain changes",
                        "navigation")));
    }

    @Override
    protected String successMessage() {
        return "Mined " + minedCount + " blocks of " + record.blockType;
    }

    @Override
    protected String timeoutMessage() {
        return "Mining timed out after mining " + minedCount + "/" + record.count
                + " blocks of " + record.blockType;
    }

    @Override
    protected String failureMessage() {
        return failReason != null ? failReason
                : "Mining failed after mining " + minedCount + "/" + record.count
                + " blocks of " + record.blockType;
    }
}
