package com.mineagent.gametest;

import com.mineagent.engine.MineAgentEngine;
import com.mineagent.engine.chat.CommandParser;
import com.mineagent.engine.entity.CompanionEntity;
import com.mineagent.engine.learning.DeathNote;
import com.mineagent.engine.loop.AgentLoop;
import com.mineagent.engine.planning.GoalBook;
import com.mineagent.engine.task.TaskContext;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The companion in a running game (SMH plan 69, MA4.4), on a headless server in CI: what the unit tests cannot show.
 * No model is needed for these scenes: the companion points at a closed local port, so every model call fails at
 * once and only the game-side code acts (combat, guard duty, deaths, the goal book). Husks stand in for zombies:
 * they do not burn in daylight, which would kill them for the companion. Each scene runs alone (its own batch) and
 * first removes any companion an earlier scene left behind.
 */
public class CompanionGameTests implements FabricGameTest {
    private static final String NO_MODEL = "http://127.0.0.1:9";
    /** A dedicated server's default. */
    private static final int SIMULATION_DISTANCE = 10;

    static {
        System.setProperty("mineagent.traceFights", "true"); // fights print what the companion saw and did
    }

    private record Scene(ServerPlayer owner, CompanionEntity companion, ServerPlayer body, AgentLoop loop) {}

    /** A stone floor, an owner (a mock player) at (3, 1, 3), and a companion beside them, with no model. */
    private static Scene scene(GameTestHelper helper, String name) {
        return scene(helper, name, false, null);
    }

    /**
     * With {@code installed}, the companion uses the mod's own settings as a fresh install has them (SMH on this PC,
     * where FakeSmh stands in); {@code owner}: an owner to reuse (null: a new mock player).
     */
    private static Scene scene(GameTestHelper helper, String name, boolean installed, ServerPlayer reuseOwner) {
        // The test server keeps entities ticking only in the chunk a player stands in; a real server does so for 5 to
        // 32 chunks around each player (10 by default). With none, a mob a step across a chunk border froze in place
        // mid-fight. Give the scenes a real server's simulation distance.
        var players = helper.getLevel().getServer().getPlayerList();
        if (players.getSimulationDistance() < SIMULATION_DISTANCE) players.setSimulationDistance(SIMULATION_DISTANCE);
        for (var s : List.copyOf(MineAgentEngine.allCompanions())) {
            MineAgentEngine.despawnCompanion(s.companion.companionId(), true);
        }
        // Scenes build past their own test area, and the areas sit close together: clear what an earlier scene left
        // above this floor (walls, roofs, gravel, lava, and blocks it marked as a player's) before laying it.
        var placed = com.mineagent.engine.world.PlacedBlocks.of(helper.getLevel());
        for (int x = -16; x <= 24; x++) {
            for (int z = -16; z <= 24; z++) {
                for (int y = 12; y >= 1; y--) helper.setBlock(x, y, z, Blocks.AIR);
                helper.setBlock(x, 0, z, Blocks.STONE);
                // Under the floor too: the swimming scene digs a lake down to the bedrock at -3.
                helper.setBlock(x, -1, z, Blocks.DIRT);
                helper.setBlock(x, -2, z, Blocks.DIRT);
                for (int y = 0; y <= 12; y++) placed.clear(helper.absolutePos(new BlockPos(x, y, z)));
            }
        }
        ServerPlayer owner = reuseOwner != null ? reuseOwner : helper.makeMockServerPlayerInLevel();
        BlockPos at = helper.absolutePos(new BlockPos(3, 1, 3));
        owner.teleportTo(helper.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0f, 0.0f);
        var llm = MineAgentEngine.getConfig().llm();
        CompanionEntity companion = installed
                ? MineAgentEngine.spawnCompanion(owner, name, llm.provider(), llm.apiKey(), llm.model(),
                        llm.baseUrl().isEmpty() ? null : llm.baseUrl(), llm.temperature(), null, false)
                : MineAgentEngine.spawnCompanion(owner, name, "openai-compatible", "test",
                        "gametest-" + name.toLowerCase(), NO_MODEL, 0.2, null, false);
        if (companion == null) throw new IllegalStateException("the companion did not spawn");
        AgentLoop loop = TaskContext.agentLoop(companion);
        if (loop == null) throw new IllegalStateException("the companion has no loop");
        return new Scene(owner, companion, companion.serverPlayer(), loop);
    }

    private static void armed(ServerPlayer body) {
        body.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
        body.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        body.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        body.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
    }

    private static void tidy(Scene s) {
        MineAgentEngine.despawnCompanion(s.companion().companionId(), true);
        s.owner().getServer().getPlayerList().remove(s.owner());
    }

    /**
     * Where a block is in the scene's own coordinates. 1.21.1's {@code GameTestHelper.relativePos} turns x and z
     * around (it gives (-10, 1, -10) for a block set at (10, 1, 10)); our scenes are not rotated, so this is the
     * offset from the scene's origin.
     */
    private static BlockPos rel(GameTestHelper helper, BlockPos absolute) {
        return absolute.subtract(helper.absolutePos(BlockPos.ZERO));
    }

    /** Every 40 ticks, where the companion is and what has its body, into CI's log. */
    private static void trace(GameTestHelper helper, Scene s, String what) {
        if (helper.getTick() % 40 != 0) return;
        var state = MineAgentEngine.getCompanion(s.companion().companionId());
        String control = state.map(c -> c.auction.inControl()).orElse("gone");
        System.out.println("[SCENE " + what + "] tick " + helper.getTick() + ": at "
                + rel(helper, s.body().blockPosition()).toShortString() + ", " + control
                + (s.body().isSleeping() ? ", asleep" : ""));
    }

    /** A scene's failure, also printed, so the reason is in CI's log (the report file is not). */
    private static void fail(GameTestHelper helper, String why) {
        System.out.println("[GAMETEST FAILED] " + why);
        helper.fail(why);
    }

    private static double flat(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Scene 6: a husk comes at it; it fights back and kills it. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_fight", timeoutTicks = 2400)
    public void aHuskThatAttacksItIsKilled(GameTestHelper helper) {
        Scene s = scene(helper, "Fighter");
        armed(s.body());
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(6, 1, 3));
        husk.setTarget(s.body());
        var server = helper.getLevel().getServer();
        int serverStart = server.getTickCount();
        long gameStart = helper.getLevel().getGameTime();
        helper.onEachTick(() -> {
            if (!s.body().isAlive()) {
                tidy(s);
                fail(helper, "the companion died to one husk with an iron sword and a shield");
            } else if (!husk.isAlive()) {
                boolean byIt = husk.getLastHurtByMob() == s.body();
                tidy(s);
                if (byIt) helper.succeed();
                else fail(helper, "the husk died, but not by the companion");
            } else if (helper.getTick() >= 2300) {
                String how = String.format("the husk still lives after 2300 ticks: husk health %.0f, %.1f blocks away, companion health %.0f"
                        + " (%d server ticks and %d of game time went by; day time %d; simulation distance %d; husk's spot"
                        + " entity-ticking %b)",
                        husk.getHealth(), husk.distanceTo(s.body()), s.body().getHealth(),
                        server.getTickCount() - serverStart, helper.getLevel().getGameTime() - gameStart,
                        helper.getLevel().getDayTime(), server.getPlayerList().getSimulationDistance(),
                        helper.getLevel().isPositionEntityTicking(husk.blockPosition()));
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA5.8: it hits from a player's reach (3 blocks, eyes to hitbox), not from 4.5 blocks feet to feet as it
     * used to. A still pig 4 blocks away is out of reach; 2 blocks away it is in reach. (A pig, so its fighting mind
     * leaves it be; the companion is put back in place before each try, so nothing it does in between counts.)
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_reach", timeoutTicks = 100)
    public void itHitsOnlyFromAPlayersReach(GameTestHelper helper) {
        Scene s = scene(helper, "Reach");
        BlockPos me = helper.absolutePos(new BlockPos(3, 1, 8));
        Runnable stand = () -> s.body().teleportTo(helper.getLevel(), me.getX() + 0.5, me.getY(), me.getZ() + 0.5, -90.0f, 0.0f);
        var pig = helper.spawn(EntityType.PIG, new BlockPos(7, 1, 8));
        pig.setNoAi(true);
        helper.runAfterDelay(5, () -> {
            stand.run();
            boolean far = com.mineagent.engine.act.Interaction.attackEntity(s.body(), pig);
            BlockPos near = helper.absolutePos(new BlockPos(5, 1, 8));
            pig.teleportTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
            stand.run();
            boolean close = com.mineagent.engine.act.Interaction.attackEntity(s.body(), pig);
            String where = String.format("companion at %s, pig at %s; reach %.2f, in reach %b, in sight %b, alive %b",
                    s.body().position(), pig.position(), s.body().entityInteractionRange(),
                    com.mineagent.engine.act.Interaction.inEntityReach(s.body(), pig), s.body().hasLineOfSight(pig),
                    pig.isAlive());
            tidy(s);
            if (far) fail(helper, "it hit a pig 4 blocks away (" + where + ")");
            else if (!close) fail(helper, "it could not hit a pig 2 blocks away (" + where + ")");
            else helper.succeed();
        });
    }

    /**
     * Plan 70, MA5.1: a block's break cost is its mining time with the best tool carried plus the hole; nothing built is
     * broken to get somewhere, nor anything that would let lava in or drop a tall stack of gravel.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_breaks", timeoutTicks = 100)
    public void breakCostsAreTheGamesAndSafe(GameTestHelper helper) {
        Scene s = scene(helper, "Digger");
        helper.setBlock(8, 1, 8, Blocks.STONE);
        helper.setBlock(10, 1, 8, Blocks.OAK_PLANKS);
        helper.setBlock(18, 1, 8, Blocks.STONE);
        helper.setBlock(19, 1, 8, Blocks.LAVA); // far from the planks, so nothing catches
        helper.setBlock(8, 1, 14, Blocks.STONE);
        for (int y = 2; y <= 4; y++) helper.setBlock(8, y, 14, Blocks.GRAVEL);
        helper.setBlock(11, 1, 14, Blocks.STONE);
        for (int y = 2; y <= 11; y++) helper.setBlock(11, y, 14, Blocks.GRAVEL);
        helper.runAfterDelay(2, () -> {
            var caches = TaskContext.navCaches(s.companion());
            java.util.function.Function<int[], Double> cost = xyz -> {
                var ctx = new com.mineagent.engine.pathing.moves.CalculationContext(caches.level(), caches.worldView(),
                        caches.loadedChunks()::isChunkLoaded, true, false);
                ctx.withBreakTimer(com.mineagent.engine.pathing.moves.ToolTimer.of(s.body()));
                BlockPos at = helper.absolutePos(new BlockPos(xyz[0], xyz[1], xyz[2]));
                return com.mineagent.engine.pathing.moves.MovementHelper.costOfBreaking(ctx, at.getX(), at.getY(), at.getZ());
            };
            double byHand = cost.apply(new int[] {8, 1, 8});
            s.body().getInventory().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
            double withPick = cost.apply(new int[] {8, 1, 8});
            double planks = cost.apply(new int[] {10, 1, 8});
            double byLava = cost.apply(new int[] {18, 1, 8});
            double underGravel = cost.apply(new int[] {8, 1, 14});
            double underTall = cost.apply(new int[] {11, 1, 14});
            String all = String.format("hand %.1f, pickaxe %.1f, planks %.1f, by lava %.1f, under 3 gravel %.1f, under 10 gravel %.1f",
                    byHand, withPick, planks, byLava, underGravel, underTall);
            tidy(s);
            if (!(Double.isFinite(byHand) && Double.isFinite(withPick) && withPick < byHand)) fail(helper, "stone: " + all);
            else if (Double.isFinite(planks)) fail(helper, "planks would be broken to get somewhere: " + all);
            else if (Double.isFinite(byLava)) fail(helper, "a block beside lava would be broken: " + all);
            else if (!(Double.isFinite(underGravel) && underGravel > withPick)) fail(helper, "gravel above: " + all);
            else if (Double.isFinite(underTall)) fail(helper, "a block under ten gravel would be broken: " + all);
            else helper.succeed();
        });
    }

    /**
     * A house of planks from (6, 1, 6) to (14, 4, 14) with a roof, its door at (10, 1, 14) facing south, the companion
     * inside at (10, 1, 10). Returns the goal just past the back wall, at (10, 1, 3).
     */
    private static BlockPos house(GameTestHelper helper, Scene s, net.minecraft.world.level.block.Block door) {
        for (int x = 6; x <= 14; x++) {
            for (int z = 6; z <= 14; z++) {
                boolean edge = x == 6 || x == 14 || z == 6 || z == 14;
                for (int y = 1; y <= 3; y++) if (edge) helper.setBlock(x, y, z, Blocks.OAK_PLANKS);
                helper.setBlock(x, 4, z, Blocks.OAK_PLANKS);
            }
        }
        var lower = door.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.SOUTH)
                .setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                        net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER);
        helper.setBlock(new BlockPos(10, 1, 14), lower);
        helper.setBlock(new BlockPos(10, 2, 14), lower.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        BlockPos inside = helper.absolutePos(new BlockPos(10, 1, 10));
        s.body().teleportTo(helper.getLevel(), inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5, 0.0f, 0.0f);
        System.out.println("[SCENE house] put inside at " + rel(helper, s.body().blockPosition()).toShortString()
                + "; wall at (10, 1, 6) is " + helper.getBlockState(new BlockPos(10, 1, 6)).getBlock());
        return helper.absolutePos(new BlockPos(10, 1, 3));
    }

    /** A stone button on the wall beside the door, at (9, 2, 13) inside or (9, 2, 15) outside. */
    private static void button(GameTestHelper helper, boolean inside) {
        helper.setBlock(new BlockPos(9, 2, inside ? 13 : 15), Blocks.STONE_BUTTON.defaultBlockState()
                .setValue(net.minecraft.world.level.block.ButtonBlock.FACE,
                        net.minecraft.world.level.block.state.properties.AttachFace.WALL)
                .setValue(net.minecraft.world.level.block.ButtonBlock.FACING,
                        inside ? net.minecraft.core.Direction.NORTH : net.minecraft.core.Direction.SOUTH));
    }

    private static void sendTo(GameTestHelper helper, Scene s, BlockPos goal, String id) {
        helper.runAfterDelay(5, () -> {
            var args = new com.google.gson.JsonObject();
            args.addProperty("goal_mode", "xzy");
            args.addProperty("x", goal.getX());
            args.addProperty("y", goal.getY());
            args.addProperty("z", goal.getZ());
            new com.mineagent.tools.movement.MoveToTool().onServerCall(id, args, s.companion(), reply -> {});
        });
    }

    private static int brokenWall(GameTestHelper helper) {
        int broken = 0;
        for (int x = 6; x <= 14; x++) {
            for (int z = 6; z <= 14; z++) {
                boolean edge = x == 6 || x == 14 || z == 6 || z == 14;
                if (!edge || (x == 10 && z == 14)) continue;
                for (int y = 1; y <= 3; y++) if (!helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.OAK_PLANKS)) broken++;
            }
        }
        return broken;
    }

    /** Whether its feet are in the doorway or the step just outside it. */
    private static boolean atDoor(GameTestHelper helper, Scene s) {
        BlockPos rel = rel(helper, s.body().blockPosition());
        return rel.getX() == 10 && (rel.getZ() == 14 || rel.getZ() == 15);
    }

    private static boolean doorOpen(GameTestHelper helper) {
        var state = helper.getBlockState(new BlockPos(10, 1, 14));
        return state.hasProperty(net.minecraft.world.level.block.DoorBlock.OPEN)
                && state.getValue(net.minecraft.world.level.block.DoorBlock.OPEN);
    }

    /**
     * Plan 70, MA5.1 and MA5.3: shut in a house of planks with its door on the far side, sent just past the back wall,
     * it goes out through the door and around, and shuts the door behind it. It used to dig two blocks through the wall.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_house", timeoutTicks = 1600)
    public void itUsesTheDoorNotTheWall(GameTestHelper helper) {
        Scene s = scene(helper, "Housemate");
        BlockPos goal = house(helper, s, Blocks.OAK_DOOR);
        sendTo(helper, s, goal, "house-1");
        boolean[] door = {false};
        helper.onEachTick(() -> {
            trace(helper, s, "house");
            if (atDoor(helper, s)) door[0] = true;
            int broken = brokenWall(helper);
            double off = flat(s.body().position(), Vec3.atBottomCenterOf(goal));
            if (broken > 0) {
                tidy(s);
                fail(helper, broken + " planks of the house were broken");
            } else if (off <= 1.5 && !doorOpen(helper)) {
                tidy(s);
                if (door[0]) helper.succeed();
                else fail(helper, "it got there without going through the door");
            } else if (helper.getTick() >= 1500) {
                String how = String.format("after 1500 ticks: %.1f blocks off, at %s, door %s", off,
                        s.body().blockPosition().toShortString(), doorOpen(helper) ? "left open" : "shut");
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /** Plan 70, MA5.3: an iron door with a button inside: it presses the button and goes out, the wall left whole. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_iron_door", timeoutTicks = 1600)
    public void itPressesTheButtonOfAnIronDoor(GameTestHelper helper) {
        Scene s = scene(helper, "Doorman");
        BlockPos goal = house(helper, s, Blocks.IRON_DOOR);
        button(helper, true);
        button(helper, false);
        sendTo(helper, s, goal, "iron-1");
        boolean[] door = {false};
        helper.onEachTick(() -> {
            if (atDoor(helper, s)) door[0] = true;
            int broken = brokenWall(helper);
            double off = flat(s.body().position(), Vec3.atBottomCenterOf(goal));
            if (broken > 0 || !helper.getBlockState(new BlockPos(10, 1, 14)).is(Blocks.IRON_DOOR)) {
                tidy(s);
                fail(helper, "the house or its iron door was broken");
            } else if (off <= 1.5) {
                tidy(s);
                if (door[0]) helper.succeed();
                else fail(helper, "it got there without going through the iron door");
            } else if (helper.getTick() >= 1500) {
                String how = String.format("after 1500 ticks: %.1f blocks off, at %s", off, s.body().blockPosition().toShortString());
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA5.3: an iron door with nothing to open it is a wall: it stays inside and breaks nothing. The floor is
     * one its owner laid (as a house's floor is), so digging under the wall is out too; natural ground under a wall
     * may be dug, as a player would.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_iron_wall", timeoutTicks = 700)
    public void anIronDoorWithNoButtonIsAWall(GameTestHelper helper) {
        Scene s = scene(helper, "Shutin");
        BlockPos goal = house(helper, s, Blocks.IRON_DOOR);
        var placed = com.mineagent.engine.world.PlacedBlocks.of(helper.getLevel());
        for (int x = 6; x <= 14; x++) {
            for (int z = 6; z <= 14; z++) placed.mark(helper.absolutePos(new BlockPos(x, 0, z)));
        }
        int[] floor = {0};
        sendTo(helper, s, goal, "iron-2");
        helper.onEachTick(() -> {
            trace(helper, s, "iron wall");
            floor[0] = 0;
            for (int x = 7; x <= 13; x++) {
                for (int z = 7; z <= 13; z++) if (!helper.getBlockState(new BlockPos(x, 0, z)).is(Blocks.STONE)) floor[0]++;
            }
            if (brokenWall(helper) > 0 || !helper.getBlockState(new BlockPos(10, 1, 14)).is(Blocks.IRON_DOOR)) {
                tidy(s);
                fail(helper, "it broke out of the house");
            } else if (floor[0] > 0) {
                tidy(s);
                fail(helper, "it dug " + floor[0] + " blocks of the floor its owner laid");
            } else if (helper.getTick() >= 600) {
                BlockPos at = s.body().blockPosition();
                BlockPos rel = rel(helper, at);
                boolean inside = rel.getX() > 6 && rel.getX() < 14 && rel.getZ() > 6 && rel.getZ() < 14;
                tidy(s);
                if (inside) helper.succeed();
                else fail(helper, "it got out of a house it could not open: at " + rel.toShortString());
            }
        });
    }

    /** A bed from (x, 1, z) to (x + 1, 1, z), head to the east. */
    private static void bed(GameTestHelper helper, int x, int z) {
        var foot = Blocks.RED_BED.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.EAST)
                .setValue(net.minecraft.world.level.block.BedBlock.PART,
                        net.minecraft.world.level.block.state.properties.BedPart.FOOT);
        helper.setBlock(new BlockPos(x, 1, z), foot);
        helper.setBlock(new BlockPos(x + 1, 1, z), foot.setValue(net.minecraft.world.level.block.BedBlock.PART,
                net.minecraft.world.level.block.state.properties.BedPart.HEAD));
    }

    /** Only the owner and its companion in the world: any player left awake keeps the night from passing. */
    private static void onlyUs(GameTestHelper helper, Scene s) {
        for (var p : List.copyOf(helper.getLevel().players())) {
            if (p != s.owner() && p != s.body() && !MineAgentEngine.isCompanionPlayer(p.getUUID())) {
                p.getServer().getPlayerList().remove(p);
            }
        }
    }

    /**
     * Plan 70, MA5.5: the night passes only when everyone in the Overworld sleeps, the companion too. The owner lies
     * down at night; the companion goes to the free bed near by and sleeps; the game passes the night.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_sleep", timeoutTicks = 1400)
    public void theNightPassesWhenItSleepsToo(GameTestHelper helper) {
        Scene s = scene(helper, "Sleeper");
        onlyUs(helper, s);
        bed(helper, 8, 3);
        bed(helper, 8, 7);
        var level = helper.getLevel();
        long dayStart = level.getDayTime() / 24000L * 24000L;
        level.setDayTime(dayStart + 14000L);
        BlockPos byBed = helper.absolutePos(new BlockPos(7, 1, 3));
        s.owner().teleportTo(level, byBed.getX() + 0.5, byBed.getY(), byBed.getZ() + 0.5, 0.0f, 0.0f);
        boolean[] sawItAsleep = {false};
        helper.runAfterDelay(5, () -> {
            var tried = s.owner().startSleepInBed(helper.absolutePos(new BlockPos(8, 1, 3)));
            if (tried.left().isPresent()) {
                tidy(s);
                fail(helper, "the owner could not lie down: " + tried.left().get());
            }
        });
        helper.onEachTick(() -> {
            // A test's mock player has no client connection to tick it, so its time in bed never counts: tick it as
            // the connection would (the companion is ticked through its own).
            s.owner().doTick();
            trace(helper, s, "sleep");
            if (s.body().isSleeping()) sawItAsleep[0] = true;
            if (level.getDayTime() >= dayStart + 24000L) {
                tidy(s);
                if (sawItAsleep[0]) helper.succeed();
                else fail(helper, "the night passed without the companion asleep");
            } else if (helper.getTick() >= 1300) {
                String how = String.format("still night after 1300 ticks: owner asleep %b, companion asleep %b, companion at %s",
                        s.owner().isSleeping(), s.body().isSleeping(), rel(helper, s.body().blockPosition()).toShortString());
                level.setDayTime(dayStart + 25000L);
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /** Plan 70, MA5.5: with no bed for the companion, the owner asleep alone does not pass the night. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_sleepless", timeoutTicks = 600)
    public void withoutItAsleepTheNightWaits(GameTestHelper helper) {
        Scene s = scene(helper, "Sleepless");
        onlyUs(helper, s);
        bed(helper, 8, 3);
        var level = helper.getLevel();
        long dayStart = level.getDayTime() / 24000L * 24000L;
        level.setDayTime(dayStart + 14000L);
        BlockPos byBed = helper.absolutePos(new BlockPos(7, 1, 3));
        s.owner().teleportTo(level, byBed.getX() + 0.5, byBed.getY(), byBed.getZ() + 0.5, 0.0f, 0.0f);
        helper.runAfterDelay(5, () -> s.owner().startSleepInBed(helper.absolutePos(new BlockPos(8, 1, 3))));
        helper.onEachTick(() -> {
            s.owner().doTick(); // as in the scene above: the mock player's time in bed counts only when it is ticked
            if (level.getDayTime() >= dayStart + 24000L) {
                tidy(s);
                fail(helper, "the night passed with the companion awake");
            } else if (helper.getTick() >= 500) {
                boolean ownerAsleep = s.owner().isSleeping() && s.owner().isSleepingLongEnough();
                level.setDayTime(dayStart + 25000L);
                tidy(s);
                if (ownerAsleep) helper.succeed();
                else fail(helper, "the owner never slept long enough to pass a night, so this shows nothing");
            }
        });
    }

    /**
     * Plan 70, MA5.4: the owner says "afk" out in the open: its duty becomes guarding them, as on "guard me". "Back"
     * ends it, as their next input would. (Their idle timer counts too; three minutes is too long for a scene.)
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_afk", timeoutTicks = 200)
    public void anAfkOwnerInTheOpenIsGuarded(GameTestHelper helper) {
        Scene s = scene(helper, "Watcher");
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "afk"));
        boolean[] guarded = {false};
        helper.onEachTick(() -> {
            var setup = s.loop().combatSetup(s.body(), s.owner());
            boolean guarding = setup.duty() == com.mineagent.engine.combat.CombatBrain.Duty.GUARD
                    && s.owner().getUUID().equals(setup.ward());
            if (!guarded[0] && guarding && s.loop().ownerAfk()) {
                guarded[0] = true;
                MineAgentEngine.onPlayerChat(s.owner(), "back");
            } else if (guarded[0] && !s.loop().ownerAfk()) {
                boolean stoodDown = s.loop().combatSetup(s.body(), s.owner()).duty()
                        != com.mineagent.engine.combat.CombatBrain.Duty.GUARD;
                tidy(s);
                if (stoodDown) helper.succeed();
                else fail(helper, "the owner is back, but it still guards them");
            } else if (helper.getTick() >= 150) {
                String how = "after 150 ticks: afk " + s.loop().ownerAfk() + ", duty " + setup.duty();
                tidy(s);
                fail(helper, guarded[0] ? "it never saw the owner come back: " + how : "it never guarded the AFK owner: " + how);
            }
        });
    }

    /**
     * Plan 70, MA5.2: a stone block the owner places is remembered and never broken to get somewhere; stone the world
     * has is still the land; breaking the owner's block strikes it off.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_placed", timeoutTicks = 100)
    public void blocksAPlayerPlacedAreLeftStanding(GameTestHelper helper) {
        Scene s = scene(helper, "Mason");
        var level = helper.getLevel();
        s.owner().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 4));
        BlockPos floor = helper.absolutePos(new BlockPos(6, 0, 6));
        var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0),
                net.minecraft.core.Direction.UP, floor, false);
        s.owner().gameMode.useItemOn(s.owner(), level, s.owner().getMainHandItem(),
                net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        helper.setBlock(9, 1, 6, Blocks.STONE);
        BlockPos theirs = helper.absolutePos(new BlockPos(6, 1, 6));
        BlockPos nature = helper.absolutePos(new BlockPos(9, 1, 6));
        helper.runAfterDelay(2, () -> {
            var record = com.mineagent.engine.world.PlacedBlocks.of(level);
            var caches = TaskContext.navCaches(s.companion());
            var ctx = new com.mineagent.engine.pathing.moves.CalculationContext(caches.level(), caches.worldView(),
                    caches.loadedChunks()::isChunkLoaded, true, false);
            boolean placedIt = level.getBlockState(theirs).is(Blocks.STONE);
            boolean remembered = record.isPlaced(theirs.getX(), theirs.getY(), theirs.getZ());
            boolean natureRemembered = record.isPlaced(nature.getX(), nature.getY(), nature.getZ());
            double theirsCost = com.mineagent.engine.pathing.moves.MovementHelper.costOfBreaking(ctx, theirs.getX(), theirs.getY(), theirs.getZ());
            double natureCost = com.mineagent.engine.pathing.moves.MovementHelper.costOfBreaking(ctx, nature.getX(), nature.getY(), nature.getZ());
            level.destroyBlock(theirs, false);
            boolean forgotten = !record.isPlaced(theirs.getX(), theirs.getY(), theirs.getZ());
            String all = String.format("placed %b, remembered %b, nature remembered %b, costs %.1f and %.1f, forgotten after breaking %b",
                    placedIt, remembered, natureRemembered, theirsCost, natureCost, forgotten);
            tidy(s);
            if (placedIt && remembered && !natureRemembered && Double.isInfinite(theirsCost) && Double.isFinite(natureCost) && forgotten) {
                helper.succeed();
            } else {
                fail(helper, all);
            }
        });
    }

    /**
     * Plan 70, MA5.9: the owner leaves and comes back. The companion comes back once, where it was, with what it had:
     * its items, its hunger, an effect on it, its bed's spawn point, and its guard post.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_return", timeoutTicks = 200)
    public void aLeaveAndReturnKeepsWhatItHad(GameTestHelper helper) {
        Scene s = scene(helper, "Keeper2");
        var body = s.body();
        body.getInventory().setItem(3, new ItemStack(Items.IRON_INGOT, 17));
        body.getFoodData().setFoodLevel(13);
        body.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 6000, 1));
        BlockPos spawn = helper.absolutePos(new BlockPos(12, 1, 12));
        body.setRespawnPosition(helper.getLevel().dimension(), spawn, 0.0f, true, false);
        BlockPos post = helper.absolutePos(new BlockPos(5, 1, 5));
        s.loop().setGuard(Vec3.atBottomCenterOf(post), null, 9.0);
        helper.runAfterDelay(5, () -> {
            MineAgentEngine.onPlayerLeave(s.owner());
            boolean gone = MineAgentEngine.getCompanionsByOwner(s.owner().getUUID()).isEmpty();
            MineAgentEngine.onPlayerJoin(s.owner());
            MineAgentEngine.onPlayerJoin(s.owner()); // a second join in the same moment must not make a second one
            var back = MineAgentEngine.getCompanionsByOwner(s.owner().getUUID());
            if (back.size() != 1) {
                for (var c : back) MineAgentEngine.despawnCompanion(c.companion.companionId(), true);
                s.owner().getServer().getPlayerList().remove(s.owner());
                fail(helper, "after leaving and coming back there are " + back.size() + " companions (gone at first: " + gone + ")");
                return;
            }
            var again = back.get(0).companion.serverPlayer();
            var loop = TaskContext.agentLoop(back.get(0).companion);
            int iron = again.getInventory().countItem(Items.IRON_INGOT);
            int food = again.getFoodData().getFoodLevel();
            boolean fast = again.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);
            boolean spawnKept = spawn.equals(again.getRespawnPosition());
            boolean guarding = loop != null && loop.guarding() && loop.guardRadiusBlocks() == 9;
            String all = String.format("gone at first %b, iron %d, food %d, speed %b, spawn kept %b, guarding %b",
                    gone, iron, food, fast, spawnKept, guarding);
            MineAgentEngine.despawnCompanion(back.get(0).companion.companionId(), true);
            s.owner().getServer().getPlayerList().remove(s.owner());
            if (gone && iron == 17 && food == 13 && fast && spawnKept && guarding) helper.succeed();
            else fail(helper, all);
        });
    }

    /** On guard, a husk 14 blocks away, outside a 6-block perimeter and not moving: it stays at its post. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_guard_far", timeoutTicks = 200)
    public void onGuardItDoesNotRunOffAfterAFarMob(GameTestHelper helper) {
        Scene s = scene(helper, "Sentry");
        armed(s.body());
        if (!s.loop().ownerCommand(new CommandParser.Command(CommandParser.Kind.GUARD_HERE, null, 6))) {
            tidy(s);
            fail(helper, "guard here was refused");
            return;
        }
        Vec3 post = s.owner().position();
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(17, 1, 3));
        husk.setNoAi(true);
        husk.setTarget(s.body());
        helper.onEachTick(() -> {
            double away = flat(s.body().position(), post);
            if (away > 3.5) {
                tidy(s);
                fail(helper, "it left its post for a mob outside the perimeter: " + String.format("%.1f", away) + " blocks out");
            } else if (helper.getTick() >= 120) {
                tidy(s);
                helper.succeed();
            }
        });
    }

    /** On guard, a husk inside the perimeter is fought and killed, and it never leaves the perimeter to do it. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_guard_near", timeoutTicks = 2400)
    public void onGuardItClearsItsPerimeterWithoutLeavingIt(GameTestHelper helper) {
        Scene s = scene(helper, "Warden");
        armed(s.body());
        if (!s.loop().ownerCommand(new CommandParser.Command(CommandParser.Kind.GUARD_HERE, null, 8))) {
            tidy(s);
            fail(helper, "guard here was refused");
            return;
        }
        Vec3 post = s.owner().position();
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(8, 1, 3));
        husk.setTarget(s.body());
        helper.onEachTick(() -> {
            trace(helper, s, "guard");
            if (helper.getTick() % 40 == 0) {
                System.out.println("[SCENE guard] husk at " + rel(helper, husk.blockPosition()).toShortString() + ", health "
                        + Math.round(husk.getHealth()) + ", " + String.format("%.1f", husk.distanceTo(s.body()))
                        + " away; companion blocking " + s.body().isBlocking() + ", in reach "
                        + com.mineagent.engine.act.Interaction.inEntityReach(s.body(), husk) + ", husk's target "
                        + (husk.getTarget() == null ? "none" : husk.getTarget().getName().getString()));
            }
            double away = flat(s.body().position(), post);
            if (away > 8 + 3.5) {
                tidy(s);
                fail(helper, "it went " + String.format("%.1f", away) + " blocks from its post, past its 8-block perimeter");
            } else if (!husk.isAlive()) {
                tidy(s);
                helper.succeed();
            } else if (helper.getTick() >= 2300) {
                String how = String.format("the husk still lives after 2300 ticks: husk health %.0f, %.1f blocks from the companion, "
                        + "the companion %.1f from its post, health %.0f", husk.getHealth(), husk.distanceTo(s.body()), away, s.body().getHealth());
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /** Scene 7: it dies carrying things; a goal to go back for them is put ahead of everything else. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_death", timeoutTicks = 200)
    public void aDeathLeavesAGoalToRecoverItsThings(GameTestHelper helper) {
        Scene s = scene(helper, "Unlucky");
        s.loop().addGoal("build a shelter", "owner");
        s.body().getInventory().setItem(3, new ItemStack(Items.DIRT, 16));
        s.body().kill();
        helper.onEachTick(() -> {
            var active = s.loop().goalBook().active();
            if (active.isPresent() && active.get().text().startsWith(DeathNote.RECOVER_PREFIX)) {
                boolean queued = s.loop().goalBook().list().stream()
                        .anyMatch(g -> g.text().equals("build a shelter") && g.status() == GoalBook.Status.PAUSED);
                tidy(s);
                if (queued) helper.succeed();
                else fail(helper, "the goal it was on was not kept behind the recovery goal");
            } else if (helper.getTick() >= 150) {
                tidy(s);
                fail(helper, "no recovery goal after the death; active: " + active.map(GoalBook.Goal::text).orElse("none"));
            }
        });
    }

    private static int dirt(ServerPlayer body) {
        int n = 0;
        var inv = body.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(Items.DIRT)) n += inv.getItem(i).getCount();
        return n;
    }

    /** A second player beside the companion, who says something to it through the game's chat path. */
    private static ServerPlayer neighbour(GameTestHelper helper, Scene s) {
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        Vec3 at = s.body().position();
        p.teleportTo(helper.getLevel(), at.x + 2, at.y, at.z + 1, 0.0f, 0.0f);
        return p;
    }

    /** Scene 2: a stranger gives an order (one the owner's words would run at once), and nothing happens. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_stranger", timeoutTicks = 200)
    public void aStrangersOrderDoesNothing(GameTestHelper helper) {
        Scene s = scene(helper, "Loyal");
        s.body().getInventory().setItem(3, new ItemStack(Items.DIRT, 16));
        ServerPlayer stranger = neighbour(helper, s);
        MineAgentEngine.onPlayerChat(stranger, "Loyal, drop 16 dirt");
        MineAgentEngine.onPlayerChat(stranger, "Loyal, drop all your stuff");
        helper.onEachTick(() -> {
            if (dirt(s.body()) != 16) {
                stranger.getServer().getPlayerList().remove(stranger);
                tidy(s);
                fail(helper, "a stranger's order moved its things: " + dirt(s.body()) + " dirt left of 16");
            } else if (helper.getTick() >= 100) {
                stranger.getServer().getPlayerList().remove(stranger);
                tidy(s);
                helper.succeed();
            }
        });
    }

    /** Scene 3: a trusted player gives one, and it runs (the owner's plain commands, without a model). */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_trusted", timeoutTicks = 300)
    public void aTrustedPlayersOrderRuns(GameTestHelper helper) {
        Scene s = scene(helper, "Helper");
        s.body().getInventory().setItem(3, new ItemStack(Items.DIRT, 16));
        ServerPlayer friend = neighbour(helper, s);
        s.loop().setAgentSettings(s.loop().getAgentSettings().withTrusted(friend.getUUID()));
        MineAgentEngine.onPlayerChat(friend, "Helper, drop 4 dirt");
        helper.onEachTick(() -> {
            int left = dirt(s.body());
            if (left == 12) {
                friend.getServer().getPlayerList().remove(friend);
                tidy(s);
                helper.succeed();
            } else if (left < 12 || helper.getTick() >= 250) {
                friend.getServer().getPlayerList().remove(friend);
                tidy(s);
                fail(helper, "a trusted player's \"drop 4 dirt\" left " + left + " of 16");
            }
        });
    }

    /** Real time, for scenes that wait on a model call (the test server runs ticks as fast as it can). */
    private static long now() {
        return System.currentTimeMillis();
    }

    /**
     * A fresh install (the mod's own settings) reaches SMH on this PC with no key; a new long goal's call carries the
     * goal, what the game counts as short, and the situation brief (MA3.1, MA4.2).
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_installed", timeoutTicks = 1_000_000)
    public void aFreshInstallReachesSmhAndAGoalCarriesItsBrief(GameTestHelper helper) {
        String fake = FakeSmh.start();
        if (fake != null) {
            fail(helper, fake);
            return;
        }
        var llm = MineAgentEngine.getConfig().llm();
        if (!"http://127.0.0.1:47821/apps".equals(llm.baseUrl()) || !"smh/auto".equals(llm.model()) || !llm.apiKey().isEmpty()) {
            fail(helper, "a fresh install is not set for SMH on this PC: " + llm.provider() + " " + llm.baseUrl() + " " + llm.model());
            return;
        }
        Scene s = scene(helper, "Rookie", true, null);
        if (!"MineAgent".equals(MineAgentEngine.freeCompanionName(s.owner().getUUID(), null, "smh/auto"))) {
            tidy(s);
            fail(helper, "an SMH companion's default name is not MineAgent");
            return;
        }
        s.loop().addGoal("get 3 diamonds", "owner");
        long deadline = now() + 30_000L;
        helper.onEachTick(() -> {
            var call = FakeSmh.CALLS.stream().filter(c -> c.text().contains("Long goal") && c.text().contains("get 3 diamonds")).findFirst();
            if (call.isPresent()) {
                var c = call.get();
                String missing = c.authorization() != null ? "it sent a key"
                        : !c.path().equals("/apps/v1/chat/completions") ? "it called " + c.path()
                        : !c.model().startsWith("smh/") ? "its model was " + c.model()
                        : !c.text().contains("[SITUATION]") ? "no situation brief"
                        : !c.text().contains("Still short (counted): 3 diamond") ? "no counted shortfall"
                        : !c.hasTools() ? "the goal's call had no tools to act with" : null;
                tidy(s);
                if (missing == null) helper.succeed();
                else fail(helper, "the goal reached SMH, but " + missing);
            } else if (now() > deadline) {
                tidy(s);
                fail(helper, "no call with the goal reached SMH on this PC in 30 s (" + FakeSmh.CALLS.size() + " calls)");
            }
        });
    }

    /** Scene 1: a stranger talks and gets a reply, through a call that cannot act; their words never reach one that can. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_talk", timeoutTicks = 1_000_000)
    public void aStrangerTalksAndGetsAReplyButNoTask(GameTestHelper helper) {
        String fake = FakeSmh.start();
        if (fake != null) {
            fail(helper, fake);
            return;
        }
        Scene s = scene(helper, "Chatty", true, null);
        ServerPlayer stranger = neighbour(helper, s);
        MineAgentEngine.onPlayerChat(stranger, "Chatty, lovely weather for mining today");
        long[] heardAt = {0L};
        long deadline = now() + 30_000L;
        helper.onEachTick(() -> {
            var talk = FakeSmh.CALLS.stream().filter(c -> c.text().contains("lovely weather")).toList();
            boolean acted = talk.stream().anyMatch(FakeSmh.Call::hasTools);
            if (acted) {
                stranger.getServer().getPlayerList().remove(stranger);
                tidy(s);
                fail(helper, "a stranger's words reached a call that can act");
                return;
            }
            boolean fenced = talk.stream().anyMatch(c -> c.text().contains("UNTRUSTED PLAYER MESSAGE"));
            if (fenced && heardAt[0] == 0L) heardAt[0] = now();
            // Heard, fenced, with no tools; two seconds more to see that nothing else carries their words on.
            if (heardAt[0] != 0L && now() - heardAt[0] > 2_000L) {
                stranger.getServer().getPlayerList().remove(stranger);
                tidy(s);
                helper.succeed();
            } else if (now() > deadline) {
                stranger.getServer().getPlayerList().remove(stranger);
                tidy(s);
                fail(helper, "the stranger got no reply call in 30 s");
            }
        });
    }

    /** Scene 8: a skill it learned is still there in a new world (the permanent library outlives the world's memory). */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_library", timeoutTicks = 200)
    public void aSkillIsCarriedIntoANewWorld(GameTestHelper helper) {
        Scene s = scene(helper, "Keeper");
        String steps = "[{\"tool\":\"craft\",\"args\":{\"item\":\"minecraft:crafting_table\",\"count\":1}}]";
        s.loop().skillLibrary().registerSequence("make_a_crafting_table", "make a crafting table", "make a crafting table", steps, true);
        if (s.loop().skillLibrary().get("make_a_crafting_table").isEmpty()) {
            tidy(s);
            fail(helper, "the skill was not taken");
            return;
        }
        s.loop().saveMemoryBlocking();
        java.nio.file.Path world = s.loop().memoryFolder();
        java.nio.file.Path library = s.loop().libraryFolder();
        ServerPlayer owner = s.owner();
        MineAgentEngine.despawnCompanion(s.companion().companionId(), false);
        // A new world: this one's memory of the companion is gone; the library is not the world's.
        try {
            if (world != null && java.nio.file.Files.exists(world)) {
                try (var walk = java.nio.file.Files.walk(world)) {
                    walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                }
            }
        } catch (java.io.IOException e) {
            fail(helper, "could not clear the world's memory: " + e.getMessage());
            return;
        }
        if (library == null || !java.nio.file.Files.exists(library.resolve("skills.json"))) {
            fail(helper, "the skill was not written to the permanent library (" + library + ")");
            return;
        }
        Scene again = scene(helper, "Keeper", false, owner);
        boolean kept = again.loop().skillLibrary().get("make_a_crafting_table").isPresent();
        tidy(again);
        if (kept) helper.succeed();
        else fail(helper, "the skill did not come back in the new world");
    }

    /** "get 3 diamonds" becomes a goal whose amount is counted, the item checked against the game's registry. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_goal", timeoutTicks = 100)
    public void aGoalsAmountsAreReadAgainstTheGame(GameTestHelper helper) {
        Scene s = scene(helper, "Planner");
        s.loop().addGoal("get 3 diamonds and 2 unobtainium", "owner");
        var active = s.loop().goalBook().active();
        tidy(s);
        if (active.isEmpty()) {
            fail(helper, "the goal was not added");
        } else if (!active.get().needs().equals(List.of(new GoalBook.Need("diamond", 3)))) {
            fail(helper, "needs read as " + active.get().needs() + ", expected 3 diamond only");
        } else {
            helper.succeed();
        }
    }

    /** A corridor two blocks high through solid stone: in at (0, 8), east to x 6, north to z 2, east to x 12, south to z 12. */
    private static boolean corridor(int x, int z) {
        return (z == 8 && x >= 0 && x <= 6) || (x == 6 && z >= 2 && z <= 8) || (z == 2 && x >= 6 && x <= 12)
                || (x == 12 && z >= 2 && z <= 12);
    }

    /**
     * Plan 70, MA5.6: it walks into a winding cave, and "lead me out" takes it back out the way it came, by its trail,
     * breaking nothing.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_cave", timeoutTicks = 1600)
    public void itLeadsTheWayOutOfACaveByItsTrail(GameTestHelper helper) {
        Scene s = scene(helper, "Caver");
        var level = helper.getLevel();
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        BlockPos away = helper.absolutePos(new BlockPos(-12, 1, -12));
        s.owner().teleportTo(level, away.getX() + 0.5, away.getY(), away.getZ() + 0.5, 0.0f, 0.0f);
        BlockPos outside = helper.absolutePos(new BlockPos(-3, 1, 8));
        s.body().teleportTo(level, outside.getX() + 0.5, outside.getY(), outside.getZ() + 0.5, 0.0f, 0.0f);
        for (int x = 0; x <= 14; x++) {
            for (int z = 0; z <= 14; z++) {
                for (int y = 1; y <= 5; y++) helper.setBlock(x, y, z, y <= 2 && corridor(x, z) ? Blocks.AIR : Blocks.STONE);
            }
        }
        BlockPos end = helper.absolutePos(new BlockPos(12, 1, 12));
        sendTo(helper, s, end, "cave-in");
        int[] phase = {0};
        helper.onEachTick(() -> {
            trace(helper, s, "cave");
            int broken = 0;
            for (int x = 0; x <= 14; x++) {
                for (int z = 0; z <= 14; z++) {
                    for (int y = 1; y <= 5; y++) {
                        if (!(y <= 2 && corridor(x, z)) && !helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.STONE)) broken++;
                    }
                }
            }
            BlockPos at = rel(helper, s.body().blockPosition());
            boolean open = !com.mineagent.engine.world.PlaceKeeper.underground(level, s.body().blockPosition());
            if (broken > 0) {
                tidy(s);
                fail(helper, "it broke " + broken + " blocks of the cave");
            } else if (phase[0] == 0 && helper.getTick() > 10 && flat(s.body().position(), Vec3.atBottomCenterOf(end)) <= 1.5) {
                phase[0] = 1;
                MineAgentEngine.onPlayerChat(s.owner(), "lead me out");
            } else if (phase[0] == 1 && open && at.getX() < 0) {
                tidy(s);
                helper.succeed();
            } else if (helper.getTick() >= 1500) {
                String how = (phase[0] == 0 ? "it never got to the end of the cave" : "it never got back out")
                        + ": at " + at.toShortString() + ", " + (open ? "in the open" : "underground")
                        + ", trail of " + s.loop().placeKeeper().memory().wayBack().size() + " points";
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA5.6: the owner says "this is home"; sent far off, "go home" brings it back there, and home is in the
     * memory it saves.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_home", timeoutTicks = 1400)
    public void goHomeTakesItBackToTheHomeItWasShown(GameTestHelper helper) {
        Scene s = scene(helper, "Homebody");
        var level = helper.getLevel();
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        BlockPos home = helper.absolutePos(new BlockPos(-12, 1, -12));
        s.owner().teleportTo(level, home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.0f, 0.0f);
        helper.runAfterDelay(2, () -> MineAgentEngine.onPlayerChat(s.owner(), "this is home"));
        BlockPos far = helper.absolutePos(new BlockPos(20, 1, 20));
        sendTo(helper, s, far, "home-out");
        int[] phase = {0};
        helper.onEachTick(() -> {
            trace(helper, s, "home");
            var memory = s.loop().placeKeeper().memory();
            var known = memory.find("home", null);
            if (phase[0] == 0 && helper.getTick() > 10 && flat(s.body().position(), Vec3.atBottomCenterOf(far)) <= 1.5) {
                phase[0] = 1;
                if (known.isEmpty() || known.get().at().x() != home.getX() || known.get().at().z() != home.getZ()) {
                    String what = known.map(p -> p.at().toString()).orElse("no home");
                    tidy(s);
                    fail(helper, "\"this is home\" did not keep where the owner stood: " + what);
                    return;
                }
                MineAgentEngine.onPlayerChat(s.owner(), "go home");
            } else if (phase[0] == 1 && flat(s.body().position(), Vec3.atBottomCenterOf(home)) <= 2.5) {
                s.loop().saveMemoryBlocking();
                var folder = s.loop().memoryFolder();
                String saved = "";
                if (folder != null) {
                    saved = com.mineagent.engine.memory.store.StoreFile.read(folder.resolve("store/routes/places.gz"))
                            .contents().map(com.mineagent.engine.memory.store.StoreFile.Contents::body).orElse("");
                }
                tidy(s);
                if (saved.contains("\"home\"")) helper.succeed();
                else fail(helper, "home was not in the saved memory store (folder " + folder + ")");
            } else if (helper.getTick() >= 1300) {
                String how = "after 1300 ticks, phase " + phase[0] + ": at " + rel(helper, s.body().blockPosition()).toShortString()
                        + ", home " + known.map(p -> p.at().toString()).orElse("not known");
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /** A straight walk of 36 blocks across the arena, the owner out of the way; whether it sprinted on the way. */
    private static void longWalk(GameTestHelper helper, String name, int food, boolean wantSprint) {
        Scene s = scene(helper, name);
        var level = helper.getLevel();
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        BlockPos aside = helper.absolutePos(new BlockPos(3, 1, -12));
        s.owner().teleportTo(level, aside.getX() + 0.5, aside.getY(), aside.getZ() + 0.5, 0.0f, 0.0f);
        BlockPos start = helper.absolutePos(new BlockPos(-14, 1, 4));
        s.body().teleportTo(level, start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0.0f, 0.0f);
        s.body().getFoodData().setFoodLevel(food);
        BlockPos goal = helper.absolutePos(new BlockPos(22, 1, 4));
        sendTo(helper, s, goal, "walk-" + name);
        int[] sprintTicks = {0};
        helper.onEachTick(() -> {
            if (s.body().isSprinting()) sprintTicks[0]++;
            double off = flat(s.body().position(), Vec3.atBottomCenterOf(goal));
            if (off <= 1.5) {
                tidy(s);
                boolean sprinted = sprintTicks[0] > 0;
                if (sprinted == wantSprint) helper.succeed();
                else fail(helper, (wantSprint ? "it walked the whole way" : "it sprinted with " + food + " food")
                        + " (" + sprintTicks[0] + " ticks sprinting, " + helper.getTick() + " ticks in all)");
            } else if (helper.getTick() >= 500) {
                String how = String.format("after 500 ticks it is %.1f blocks short, at %s", off,
                        rel(helper, s.body().blockPosition()).toShortString());
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /** Plan 70, MA5.10: on a long straight walk it sprints, as a player would. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_sprint", timeoutTicks = 600)
    public void itSprintsOnALongStraightWalk(GameTestHelper helper) {
        longWalk(helper, "Runner", 20, true);
    }

    /** Plan 70, MA5.10: with 6 food a player can't sprint, and neither does it. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_hungry_walk", timeoutTicks = 600)
    public void hungryItWalks(GameTestHelper helper) {
        longWalk(helper, "Hungry", 6, false);
    }

    /**
     * A tower of planks from (4, 1, 4) to (6, 9, 6), the companion on top at (5, 10, 5), the owner out of the way. Planks
     * are someone's building, so it never digs down through them. Returns the goal on the ground east of it.
     */
    private static BlockPos tower(GameTestHelper helper, Scene s) {
        var level = helper.getLevel();
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        BlockPos aside = helper.absolutePos(new BlockPos(-12, 1, -12));
        s.owner().teleportTo(level, aside.getX() + 0.5, aside.getY(), aside.getZ() + 0.5, 0.0f, 0.0f);
        for (int x = 4; x <= 6; x++) {
            for (int z = 4; z <= 6; z++) {
                for (int y = 1; y <= 9; y++) helper.setBlock(x, y, z, Blocks.OAK_PLANKS);
            }
        }
        BlockPos top = helper.absolutePos(new BlockPos(5, 10, 5));
        s.body().teleportTo(level, top.getX() + 0.5, top.getY(), top.getZ() + 0.5, 0.0f, 0.0f);
        return helper.absolutePos(new BlockPos(12, 1, 5));
    }

    private static int towerBroken(GameTestHelper helper) {
        int broken = 0;
        for (int x = 4; x <= 6; x++) {
            for (int z = 4; z <= 6; z++) {
                for (int y = 1; y <= 9; y++) if (!helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.OAK_PLANKS)) broken++;
            }
        }
        return broken;
    }

    /**
     * Plan 70, MA5.10: on a tower of planks with no way down but a drop of 10 blocks into one block of water, it takes
     * the drop (water of any depth breaks a fall) and lands unhurt.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_water_drop", timeoutTicks = 700)
    public void itDropsIntoWaterFromHigh(GameTestHelper helper) {
        Scene s = scene(helper, "Diver");
        BlockPos goal = tower(helper, s);
        helper.setBlock(7, -1, 5, Blocks.STONE);
        helper.setBlock(7, 0, 5, Blocks.WATER);
        sendTo(helper, s, goal, "dive-1");
        helper.onEachTick(() -> {
            trace(helper, s, "water drop");
            double off = flat(s.body().position(), Vec3.atBottomCenterOf(goal));
            if (towerBroken(helper) > 0) {
                tidy(s);
                fail(helper, "it broke the tower");
            } else if (off <= 1.5) {
                float health = s.body().getHealth();
                tidy(s);
                if (health >= 20.0f) helper.succeed();
                else fail(helper, "it got down hurt: health " + health);
            } else if (helper.getTick() >= 600) {
                String how = String.format("after 600 ticks: %.1f blocks off, at %s, health %.0f", off,
                        rel(helper, s.body().blockPosition()).toShortString(), s.body().getHealth());
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA5.10: with a water bucket, from the same tower onto plain ground: it steps off, the water goes down
     * under it before it lands, and comes back up into the bucket.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_bucket_descent", timeoutTicks = 700)
    public void itComesDownWithAWaterBucket(GameTestHelper helper) {
        Scene s = scene(helper, "Clutch");
        BlockPos goal = tower(helper, s);
        s.body().getInventory().setItem(1, new ItemStack(Items.WATER_BUCKET));
        sendTo(helper, s, goal, "bucket-1");
        helper.onEachTick(() -> {
            trace(helper, s, "bucket");
            double off = flat(s.body().position(), Vec3.atBottomCenterOf(goal));
            if (towerBroken(helper) > 0) {
                tidy(s);
                fail(helper, "it broke the tower");
            } else if (off <= 1.5) {
                float health = s.body().getHealth();
                boolean bucket = s.body().getInventory().contains(new ItemStack(Items.WATER_BUCKET));
                int water = 0;
                for (int x = 3; x <= 8; x++) {
                    for (int z = 3; z <= 7; z++) {
                        if (helper.getBlockState(new BlockPos(x, 1, z)).getFluidState().is(net.minecraft.tags.FluidTags.WATER)) water++;
                    }
                }
                tidy(s);
                if (health >= 20.0f && bucket && water == 0) helper.succeed();
                else fail(helper, "down, but health " + health + ", water bucket back " + bucket + ", water left " + water);
            } else if (helper.getTick() >= 600) {
                String how = String.format("after 600 ticks: %.1f blocks off, at %s, health %.0f", off,
                        rel(helper, s.body().blockPosition()).toShortString(), s.body().getHealth());
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA5.10: a lake three deep with an island in it, and nothing to build a bridge with: it swims there, its
     * head above the water.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_swim", timeoutTicks = 900)
    public void itSwimsToAnIsland(GameTestHelper helper) {
        Scene s = scene(helper, "Swimmer");
        var level = helper.getLevel();
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        BlockPos aside = helper.absolutePos(new BlockPos(-14, 1, -14));
        s.owner().teleportTo(level, aside.getX() + 0.5, aside.getY(), aside.getZ() + 0.5, 0.0f, 0.0f);
        for (int x = -6; x <= 8; x++) {
            for (int z = -6; z <= 8; z++) {
                boolean island = x >= 0 && x <= 2 && z >= 0 && z <= 2;
                for (int y = -2; y <= 0; y++) helper.setBlock(x, y, z, island ? Blocks.STONE : Blocks.WATER);
            }
        }
        BlockPos start = helper.absolutePos(new BlockPos(-10, 1, 1));
        s.body().teleportTo(level, start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0.0f, 0.0f);
        BlockPos goal = helper.absolutePos(new BlockPos(1, 1, 1));
        sendTo(helper, s, goal, "swim-1");
        int[] lowestAir = {Integer.MAX_VALUE};
        helper.onEachTick(() -> {
            trace(helper, s, "swim");
            lowestAir[0] = Math.min(lowestAir[0], s.body().getAirSupply());
            double off = flat(s.body().position(), Vec3.atBottomCenterOf(goal));
            if (off <= 1.0 && s.body().onGround()) {
                float health = s.body().getHealth();
                tidy(s);
                if (health >= 20.0f) helper.succeed();
                else fail(helper, "it got there hurt: health " + health + ", lowest air " + lowestAir[0]);
            } else if (helper.getTick() >= 800) {
                String how = String.format("after 800 ticks: %.1f blocks off, at %s, in water %b, lowest air %d", off,
                        rel(helper, s.body().blockPosition()).toShortString(), s.body().isInWater(), lowestAir[0]);
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA5.10: a husk (standing still, its AI off) on the straight line to the goal: the path goes round it,
     * out of its reach, and it is left alone.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_mob_round", timeoutTicks = 700)
    public void itGoesRoundAMobInItsWay(GameTestHelper helper) {
        Scene s = scene(helper, "Wary");
        var level = helper.getLevel();
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        BlockPos aside = helper.absolutePos(new BlockPos(3, 1, -14));
        s.owner().teleportTo(level, aside.getX() + 0.5, aside.getY(), aside.getZ() + 0.5, 0.0f, 0.0f);
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(4, 1, 4));
        husk.setNoAi(true);
        BlockPos start = helper.absolutePos(new BlockPos(-12, 1, 4));
        s.body().teleportTo(level, start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0.0f, 0.0f);
        BlockPos goal = helper.absolutePos(new BlockPos(20, 1, 4));
        sendTo(helper, s, goal, "round-1");
        double[] closest = {Double.MAX_VALUE};
        helper.onEachTick(() -> {
            trace(helper, s, "mob round");
            if (helper.getTick() > 5) closest[0] = Math.min(closest[0], flat(s.body().position(), husk.position()));
            double off = flat(s.body().position(), Vec3.atBottomCenterOf(goal));
            if (off <= 1.5) {
                boolean alive = husk.isAlive();
                tidy(s);
                husk.discard();
                if (closest[0] >= 4.0 && alive) helper.succeed();
                else fail(helper, String.format("it came within %.1f blocks of the husk (alive %b)", closest[0], alive));
            } else if (helper.getTick() >= 600) {
                String how = String.format("after 600 ticks: %.1f blocks off, at %s, closest %.1f", off,
                        rel(helper, s.body().blockPosition()).toShortString(), closest[0]);
                tidy(s);
                husk.discard();
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA5.7: diamond ore in plain view is remembered, and noted in the journal as a find; iron ore sealed in
     * stone is not (only what it can see); the diamond mined out is struck off. What it saw is in the store on disk.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_seen", timeoutTicks = 400)
    public void itRemembersWhatItCanSeeAndNothingThroughStone(GameTestHelper helper) {
        Scene s = scene(helper, "Looker");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        helper.setBlock(7, 1, 3, Blocks.DIAMOND_ORE);
        for (int x = 9; x <= 11; x++) for (int y = 1; y <= 3; y++) for (int z = 6; z <= 8; z++) helper.setBlock(x, y, z, Blocks.STONE);
        helper.setBlock(10, 2, 7, Blocks.IRON_ORE);
        BlockPos diamond = helper.absolutePos(new BlockPos(7, 1, 3));
        boolean[] mined = {false};
        helper.onEachTick(() -> {
            var seen = s.loop().placeKeeper().sightings();
            var body = s.body();
            String dim = body.serverLevel().dimension().location().toString();
            var at = body.blockPosition();
            boolean sawDiamond = !seen.nearest("diamond", dim, at.getX(), at.getY(), at.getZ(), 1, 64).isEmpty();
            boolean sawIron = !seen.nearest("iron", dim, at.getX(), at.getY(), at.getZ(), 1, 64).isEmpty();
            if (sawIron) {
                tidy(s);
                fail(helper, "it remembered iron ore sealed inside stone");
            } else if (sawDiamond && !mined[0]) {
                mined[0] = true;
                helper.getLevel().destroyBlock(diamond, false);
            } else if (mined[0] && !sawDiamond) {
                boolean noted = s.loop().journal().recent(50).stream()
                        .anyMatch(e -> "find".equals(e.kind()) && e.text().contains("diamond"));
                s.loop().saveMemoryBlocking();
                var folder = s.loop().memoryFolder();
                boolean onDisk = folder != null && java.nio.file.Files.exists(folder.resolve("store/sightings/index.gz"));
                tidy(s);
                if (noted && onDisk) helper.succeed();
                else fail(helper, "struck off, but journal find " + noted + ", store on disk " + onDisk);
            } else if (helper.getTick() >= 350) {
                tidy(s);
                fail(helper, mined[0] ? "the mined-out diamond was still remembered" : "it never noted the diamond in plain view");
            }
        });
    }

    /**
     * Plan 70, MA5.7 and MA5.9: a chest it looked in holds iron; "where did you put the iron?" is answered from its
     * chest memory, and "what did we do today?" from its journal, both without a model.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_recall", timeoutTicks = 200)
    public void itAnswersFromItsMemory(GameTestHelper helper) {
        Scene s = scene(helper, "Recaller");
        BlockPos chest = helper.absolutePos(new BlockPos(6, 1, 6));
        var dim = s.body().serverLevel().dimension().location().toString();
        helper.runAfterDelay(2, () -> {
            var pos = new com.mineagent.engine.world.WorldAssetIndex.Position(dim, chest.getX(), chest.getY(), chest.getZ());
            s.loop().worldAssetIndex().observeContainer("minecraft:chest@" + chest.toShortString(), pos,
                    List.of(new com.mineagent.engine.world.WorldAssetIndex.ItemObservation(0, "minecraft:iron_ingot", 32, 0, 0,
                            java.util.Set.of(), 1.0)), true, helper.getLevel().getGameTime());
            MineAgentEngine.onPlayerChat(s.owner(), "remember this as the forge");
            MineAgentEngine.onPlayerChat(s.owner(), "where did you put the iron?");
            MineAgentEngine.onPlayerChat(s.owner(), "what did we do today?");
        });
        helper.onEachTick(() -> {
            var said = s.loop().journal().recent(50).stream().filter(e -> "said".equals(e.kind())).map(e -> e.text()).toList();
            boolean iron = said.stream().anyMatch(t -> t.contains("32 iron ingot in the chest"));
            boolean today = said.stream().anyMatch(t -> t.startsWith("Today:") && t.contains("place named"));
            if (iron && today) {
                tidy(s);
                helper.succeed();
            } else if (helper.getTick() >= 150) {
                tidy(s);
                fail(helper, "said: " + said);
            }
        });
    }
}
